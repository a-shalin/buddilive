package ca.digitalcave.moss.auth.model;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthUser implements Serializable {

	private static final long serialVersionUID = 1L;

	private int id;

	private String email;
	private String firstName;
	private String identifier;
	private String lastName;
	private char[] secret;

	private transient String impersonatedIdentifier;

	private String passwordHash;
	private String activationKey;
	private boolean passwordChangeRequired;
	private Date passwordLastChanged;

	private boolean twoFactorRequired;
	private String twoFactorSecret;

	private List<String> twoFactorBackupCodes;

	private Integer version;
	private Date created;
	private Date modified;

	public int getId() {
		return id;
	}
	public void setId(final int id) {
		this.id = id;
	}
	public String getPasswordHash() {
		return passwordHash;
	}
	public void setPasswordHash(final String passwordHash) {
		this.passwordHash = passwordHash;
	}
	public String getImpersonatedIdentifier() {
		return impersonatedIdentifier;
	}
	public void setImpersonatedIdentifier(final String impersonatedIdentifier) {
		this.impersonatedIdentifier = impersonatedIdentifier;
	}
	public String getActivationKey() {
		return activationKey;
	}
	public void setActivationKey(final String activationKey) {
		this.activationKey = activationKey;
	}
	public boolean isTwoFactorSetup() {
		return StringUtils.isNotBlank(twoFactorSecret);
	}
	public boolean isTwoFactorRequired() {
		return twoFactorRequired;
	}
	public void setTwoFactorRequired(final boolean twoFactorRequired) {
		this.twoFactorRequired = twoFactorRequired;
	}
	public String getTwoFactorSecret() {
		return twoFactorSecret;
	}
	public void setTwoFactorSecret(final String twoFactorSecret) {
		this.twoFactorSecret = twoFactorSecret;
	}
	public List<String> getTwoFactorBackupCodes() {
		return twoFactorBackupCodes;
	}
	public void setTwoFactorBackupCodes(final List<String> twoFactorBackupCodes) {
		this.twoFactorBackupCodes = twoFactorBackupCodes;
	}
	public Integer getVersion() {
		return version;
	}
	public void setVersion(final Integer version) {
		this.version = version;
	}
	public Date getCreated() {
		return created;
	}
	public void setCreated(final Date created) {
		this.created = created;
	}
	public Date getModified() {
		return modified;
	}
	public void setModified(final Date modified) {
		this.modified = modified;
	}
	public boolean isPasswordChangeRequired() {
		return passwordChangeRequired;
	}
	public void setPasswordChangeRequired(final boolean passwordChangeRequired) {
		this.passwordChangeRequired = passwordChangeRequired;
	}
	public Date getPasswordLastChanged() {
		return passwordLastChanged;
	}
	public void setPasswordLastChanged(final Date passwordLastChanged) {
		this.passwordLastChanged = passwordLastChanged;
	}

	public String getEmail() {
		return email;
	}
	public void setEmail(final String email) {
		this.email = email;
	}
	public String getFirstName() {
		return firstName;
	}
	public void setFirstName(final String firstName) {
		this.firstName = firstName;
	}
	public String getIdentifier() {
		return identifier;
	}
	public void setIdentifier(final String identifier) {
		this.identifier = identifier;
	}
	public String getLastName() {
		return lastName;
	}
	public void setLastName(final String lastName) {
		this.lastName = lastName;
	}
	public char[] getSecret() {
		return secret;
	}
	public void setSecret(final char[] secret) {
		this.secret = secret;
	}

	public boolean isImpersonateAllowed(final String impersonatedUsername) {
		return false;
	}

	public <T extends AuthUser> T clone(final Class<T> targetClass) {
		T target;
		try {
			target = targetClass.getConstructor().newInstance();
		}
		catch (Exception e) {
			Logger.getLogger(this.getClass().getName()).log(Level.WARNING, e.getMessage(), e);
			throw new RuntimeException(e);
		}

		target.setId(this.getId());
		target.setIdentifier(this.getIdentifier());

		target.setActivationKey(this.getActivationKey());
		target.setCreated(this.getCreated());
		target.setEmail(this.getEmail());
		target.setFirstName(this.getFirstName());
		target.setLastName(this.getLastName());
		target.setImpersonatedIdentifier(this.getImpersonatedIdentifier());
		target.setModified(this.getModified());
		target.setPasswordChangeRequired(this.isPasswordChangeRequired());
		target.setPasswordHash(this.getPasswordHash());
		target.setPasswordLastChanged(this.getPasswordLastChanged());
		target.setSecret(this.getSecret());
		target.setTwoFactorBackupCodes(this.getTwoFactorBackupCodes());
		target.setTwoFactorRequired(this.isTwoFactorRequired());
		target.setTwoFactorSecret(this.getTwoFactorSecret());
		target.setVersion(this.getVersion());

		return target;
	}
}
