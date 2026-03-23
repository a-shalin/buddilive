package ca.digitalcave.buddi.live.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;

@Controller
public class DonationController {

	private static final Logger logger = Logger.getLogger(DonationController.class.getName());

	@Autowired
	private Users users;

	@Autowired
	private AuthenticationHelper authenticationHelper;

	@GetMapping("/donation-completed")
	public String donationCompleted(@AuthenticationPrincipal User user,
			@RequestParam(required = false) String key) {
		final boolean validKey = "e0b994b8-e939-49d8-b243-cdcd0ec7fa03".equals(key);
		if (validKey && user != null) {
			users.updateUserPremium(user, "Y");
		}

		final Thread emailThread = new Thread(() -> {
			try {
				final StringBuilder body = new StringBuilder();
				if (user == null) {
					body.append("A donation was sent by an unknown user.");
				}
				else {
					body.append("A donation was sent by user ID ").append(user.getId());
					if (user.getEmail() != null) body.append(" (email: ").append(user.getEmail()).append(")");
					body.append(".");
					if (validKey) {
						body.append("\nThe user has been upgraded to Premium access.");
					}
					else {
						body.append("\nThe key was invalid, and the user has not been upgraded to Premium access.");
					}
				}
				authenticationHelper.sendEmail("buddilivedonation@digitalcave.ca", "BuddiLive Donation", body.toString());
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Failed to send donation notification email", e);
			}
		}, "Email");
		emailThread.setDaemon(false);
		emailThread.start();

		return "redirect:/doc/donation-thanks.html";
	}
}