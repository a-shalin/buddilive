package ca.digitalcave.buddi.live.resource.buddilive;

import java.util.Properties;
import java.util.logging.Level;

import org.apache.commons.mail2.jakarta.HtmlEmail;
import org.apache.ibatis.session.SqlSession;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.buddi.live.BuddiApplication;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.model.User;

public class DonationResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}

	@Override
	protected Representation get(Variant variant) throws ResourceException {
		final BuddiApplication application = (BuddiApplication) getApplication();
		final SqlSession sqlSession = application.getSqlSessionFactory().openSession(true);
		final User user = (User) getRequest().getClientInfo().getUser();
		//Yep, you have just found my super secure method of determining if someone has actually sent the donation.  Now you, yes YOU,
		// can upgrade yourself to premium access without paying a cent!  There is nothing better in life than ripping off the little guy!
		final boolean validKey = "e0b994b8-e939-49d8-b243-cdcd0ec7fa03".equals(getQueryValue("key"));
		try {
			if (validKey && user != null){
				sqlSession.getMapper(Users.class).updateUserPremium(user, "Y");
			}
		
			final Properties config = application.getConfigProperties();
			final String fromEmail = config.getProperty("mail.smtp.from", "user@localhost");
			final Runnable emailRunnable = new Runnable() {
				public void run() {
					try {
						final StringBuilder body = new StringBuilder();
						if (user == null){
							body.append("A donation was sent by an unknown user.");
						}
						else {
							body.append("A donation was sent by user ID ").append(user.getId());
							if (user.getEmail() != null) body.append(" (email: ").append(user.getEmail()).append(")");
							body.append(".");
							if (validKey){
								body.append("\nThe user has been upgraded to Premium access.");
							}
							else {
								body.append("\nThe key was invalid, and the user has not been upgraded to Premium access.");
							}
						}
						final HtmlEmail email = application.getEmail(fromEmail, null, "buddilivedonation@digitalcave.ca");
						email.setSubject("BuddiLive Donation");
						email.setTextMsg(body.toString());
						email.send();
					}
					catch (Exception e){
						getLogger().log(Level.WARNING, "Failed to send donation notification email", e);
					}
				}
			};
			final Thread emailThread = new Thread(emailRunnable, "Email");
			emailThread.setDaemon(false);
			emailThread.start();
			
			final Reference newRef = new Reference(getRootRef().toString() + "/doc/donation-thanks.html");
			redirectSeeOther(newRef);
			return new EmptyRepresentation();
		}
		finally {
			sqlSession.close();
		}
	}
}
