package ca.digitalcave.moss.restlet.resource;

import java.io.IOException;
import java.io.Writer;

import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.representation.WriterRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.util.PasswordChecker;

public class PasswordCheckResource extends ServerResource {

	@Override
	protected Representation post(Representation entity) throws ResourceException {
		PasswordChecker c = (PasswordChecker) getApplication().getContext().getAttributes().get(PasswordChecker.class.getName());
		if (c == null) {
			c = new PasswordChecker();
			getApplication().getContext().getAttributes().put(PasswordChecker.class.getName(), c);
		}
		
		final PasswordChecker checker = c;
		
		final Form form = new Form(entity);
		final String identifier = form.getFirstValue("identifier");
		final String password = form.getFirstValue("secret");
		
		return new WriterRepresentation(MediaType.APPLICATION_JSON) {
			@Override
			public void write(Writer w) throws IOException {
				boolean result = true;
				
				w.write("{");
				
				final int score = checker.getStrengthScore(password);
				w.write("\"score\":");
				w.write(Integer.toString(score));
				
				if (checker.isLengthEnforced()) {
					final boolean passed = checker.testLength(password);
					result &= passed;
					w.write(",\"length\":");
					w.write(Boolean.toString(passed));
					w.write(",\"minLength\":");
					w.write(Integer.toString(checker.getMinimumLength()));
				}

				if (checker.isStrengthEnforced()) {
					final boolean passed = checker.testStrength(password);
					result &= passed;
					w.write(",\"strength\":");
					w.write(Boolean.toString(passed));
					w.write(",\"minStrength\":");
					w.write(Integer.toString(checker.getMinimumStrength()));
				}
				
				if (checker.isVarianceEnforced()) {
					final boolean passed = checker.testVariance(password);
					result &= passed;
					w.write(",\"variance\":");
					w.write(Boolean.toString(passed));
					w.write(",\"minVariance\":");
					w.write(Integer.toString(checker.getMinimumVariance()));
				}

				if (checker.isMultiClassEnforced()) {
					final boolean passed = checker.testMulticlass(password);
					result &= passed;
					w.write(",\"classes\":");
					w.write(Boolean.toString(passed));
					w.write(",\"minClasses\":");
					w.write(Integer.toString(checker.getMinimumClasses()));
				}
				
				if (checker.isDictionaryEnforced()) {
					final boolean passed = checker.testDictionary(password);
					result &= passed;
					w.write(",\"dictionary\":");
					w.write(Boolean.toString(passed));
				}

				if (checker.isPatternsEnforced()) {
					final boolean passed = checker.testPatterns(password);
					result &= passed;
					w.write(",\"pattern\":");
					w.write(Boolean.toString(passed));
				}

				if (checker.isHistoryEnforced()) {
					final boolean passed = checker.testHistory(identifier, password);
					result &= passed;
					w.write(",\"history\":");
					w.write(Boolean.toString(passed));
				}

				if (checker.isCustomEnforced()) {
					final boolean passed = checker.testCustom(identifier, password);
					result &= passed;
					w.write(",\"custom\":");
					w.write(Boolean.toString(passed));
				}

				w.write(",\"passed\":");
				w.write(Boolean.toString(result));
				
				w.write("}");
				w.flush();
			}
		};
	}
}
