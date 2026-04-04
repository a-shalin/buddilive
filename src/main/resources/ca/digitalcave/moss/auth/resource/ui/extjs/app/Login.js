(function() {
	"use strict";

	const authConfig = window.__authConfig || {};
	const authI18n = window.__authI18n || {};
	const messageTimers = {};

	const translate = function(key) {
		return authI18n[key] || key;
	};

	const escapeHtml = function(value) {
		if (value == null) {
			return "";
		}
		return String(value)
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/\"/g, "&quot;")
			.replace(/'/g, "&#39;");
	};

	const parseJson = function(text) {
		if (!text) {
			return null;
		}
		try {
			return JSON.parse(text);
		}
		catch (e) {
			return null;
		}
	};

	const isVisible = function(value) {
		return value !== false;
	};

	const toUrlSearchParams = function(form) {
		const formData = new FormData(form);
		const params = new URLSearchParams();
		for (const entry of formData.entries()) {
			params.append(entry[0], entry[1]);
		}
		return params;
	};

	const request = async function(url, method, body) {
		const options = {
			method: method,
			credentials: "same-origin",
			headers: {
				"Accept": "application/json"
			}
		};
		if (body != null) {
			options.headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8";
			options.body = body.toString();
		}

		const response = await fetch(url, options);
		const responseText = await response.text();
		return {
			ok: response.ok,
			status: response.status,
			text: responseText,
			json: parseJson(responseText)
		};
	};

	const renderExtraField = function(field) {
		if (!field || typeof field !== "object") {
			return "";
		}

		if (field.xtype === "label") {
			return "<div class='auth-help-label'>" + (field.html || "") + "</div>";
		}

		if (field.xtype !== "selfdocumentingfield") {
			return "";
		}

		const help = field.messageBody ? "<div class='auth-field-help'>" + field.messageBody + "</div>" : "";
		const name = escapeHtml(field.name || "");
		const value = escapeHtml(field.value || "");
		const fieldLabel = escapeHtml(field.fieldLabel || "");
		const boxLabel = field.boxLabel || "";

		if (field.type === "checkbox") {
			const checkboxId = "field-" + name + "-" + Math.floor(Math.random() * 1000000);
			return ""
				+ "<div class='auth-field-row auth-checkbox-field'>"
				+ "<label for='" + checkboxId + "'>"
				+ "<input id='" + checkboxId + "' type='checkbox' name='" + name + "' value='on' required> "
				+ (boxLabel || fieldLabel)
				+ "</label>"
				+ help
				+ "</div>";
		}

		if (field.type === "localescombobox") {
			return ""
				+ "<div class='auth-field-row'>"
				+ "<label>" + fieldLabel + "</label>"
				+ "<select name='" + name + "' data-store='locales' data-default='" + value + "' required></select>"
				+ help
				+ "</div>";
		}

		if (field.type === "currenciescombobox") {
			return ""
				+ "<div class='auth-field-row'>"
				+ "<label>" + fieldLabel + "</label>"
				+ "<select name='" + name + "' data-store='currencies' data-default='" + value + "' required></select>"
				+ help
				+ "</div>";
		}

		return ""
			+ "<div class='auth-field-row'>"
			+ "<label>" + fieldLabel + "</label>"
			+ "<input type='text' name='" + name + "' value='" + value + "' required>"
			+ help
			+ "</div>";
	};

	const renderExtraFields = function(fields) {
		if (!Array.isArray(fields) || fields.length === 0) {
			return "";
		}
		return fields.map(renderExtraField).join("");
	};

	const getResponseMessage = function(result) {
		if (result && result.json && typeof result.json.message === "string" && result.json.message.length > 0) {
			return result.json.message;
		}
		return translate("UNKNOWN_ERROR_MESSAGE");
	};

	const showMessage = function(root, messageId, text, timeoutMillis) {
		const element = root.querySelector("[data-message='" + messageId + "']");
		if (!element) {
			return;
		}

		element.innerHTML = text || "";
		element.classList.toggle("auth-visible", Boolean(text));

		if (messageTimers[messageId]) {
			clearTimeout(messageTimers[messageId]);
			delete messageTimers[messageId];
		}

		if (text && timeoutMillis && timeoutMillis > 0) {
			messageTimers[messageId] = setTimeout(function() {
				element.innerHTML = "";
				element.classList.remove("auth-visible");
				delete messageTimers[messageId];
			}, timeoutMillis);
		}
	};

	const activateTab = function(root, tabId) {
		const buttons = root.querySelectorAll("[data-tab-target]");
		const panels = root.querySelectorAll("[data-tab-panel]");

		for (const button of buttons) {
			button.classList.toggle("auth-tab-active", button.getAttribute("data-tab-target") === tabId);
		}
		for (const panel of panels) {
			panel.classList.toggle("auth-hidden", panel.getAttribute("data-tab-panel") !== tabId);
		}
	};

	const activateCard = function(root, containerName, cardName) {
		const container = root.querySelector("[data-card-container='" + containerName + "']");
		if (!container) {
			return;
		}

		container.setAttribute("data-active-card", cardName);
		const cards = container.querySelectorAll("[data-card]");
		for (const card of cards) {
			card.classList.toggle("auth-hidden", card.getAttribute("data-card") !== cardName);
		}
	};

	const loadStoreOptions = async function(storeName, url, attempt) {
		const selects = document.querySelectorAll("select[data-store='" + storeName + "']");
		if (!selects || selects.length === 0) {
			return;
		}

		const currentAttempt = Number.isInteger(attempt) ? attempt : 0;
		try {
			const result = await request(url, "GET", null);
			if (!result.ok || !result.json || !Array.isArray(result.json.data)) {
				throw new Error("Invalid store payload");
			}

			const options = [];
			for (const item of result.json.data) {
				if (!item) {
					continue;
				}
				if (!item.value) {
					options.push("<option value='' disabled>" + escapeHtml(item.text || "---") + "</option>");
					continue;
				}
				options.push("<option value='" + escapeHtml(item.value) + "'>" + escapeHtml(item.text || item.value) + "</option>");
			}

			const optionsHtml = options.join("");
			for (const select of selects) {
				const defaultValue = select.getAttribute("data-default") || "";
				select.innerHTML = optionsHtml;
				if (defaultValue) {
					select.value = defaultValue;
				}
				if (!select.value) {
					const firstOption = select.querySelector("option[value]:not([value=''])");
					if (firstOption) {
						select.value = firstOption.value;
					}
				}
			}
		}
		catch (e) {
			if (currentAttempt < 3) {
				window.setTimeout(function() {
					loadStoreOptions(storeName, url, currentAttempt + 1);
				}, 300);
			}
		}
	};

	const loadTotpSecret = async function(root) {
		const qrImage = root.querySelector("[data-totp='qr-image']");
		const secretInput = root.querySelector("[data-totp='secret-input']");
		if (!qrImage || !secretInput) {
			return;
		}

		try {
			const result = await request("authentication/totpSetup", "GET", null);
			if (!result.ok || !result.json || result.json.success !== true) {
				return;
			}
			if (result.json.totpSharedSecretQr) {
				qrImage.src = result.json.totpSharedSecretQr;
			}
			if (result.json.totpSharedSecret) {
				secretInput.value = result.json.totpSharedSecret;
			}
		}
		catch (e) {
			// Ignore; user can retry with reload button.
		}
	};

	const loadTotpBackupCodes = async function(root) {
		const textarea = root.querySelector("[data-totp='backup-codes']");
		if (!textarea) {
			return;
		}

		try {
			const result = await request("authentication/generateBackupCodes", "POST", new URLSearchParams());
			if (result.ok) {
				textarea.value = result.text || "";
			}
		}
		catch (e) {
			// Ignore.
		}
	};

	const buildLoginTab = function() {
		const rememberField = isVisible(authConfig.showRemember)
			? ""
				+ "<div class='auth-field-row auth-checkbox-field'>"
				+ "<label><input type='checkbox' name='remember' value='on'> " + escapeHtml(translate("REMEMBER_LABEL")) + "</label>"
				+ "<div class='auth-field-help'>" + translate("REMEMBER_HELP") + "</div>"
				+ "</div>"
			: "";

		const disableIpLockField = isVisible(authConfig.showDisableIpLock)
			? ""
				+ "<div class='auth-field-row auth-checkbox-field'>"
				+ "<label><input type='checkbox' name='disableIpLock' value='on'> " + escapeHtml(translate("DISABLE_IP_LOCK_LABEL")) + "</label>"
				+ "<div class='auth-field-help'>" + translate("DISABLE_IP_LOCK_HELP") + "</div>"
				+ "</div>"
			: "";

		return ""
			+ "<div class='auth-card-container' data-card-container='login'>"
			+ "<form class='auth-card auth-form' data-card='authenticate' data-submit-action='authenticate'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("IDENTIFIER_LABEL")) + "</label><input type='text' name='identifier' required autofocus></div>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("PASSWORD_LABEL")) + "</label><input type='password' name='password' required></div>"
			+ rememberField
			+ disableIpLockField
			+ renderExtraFields(authConfig.extraLoginStep1Fields)
			+ "<div class='auth-message' data-message='messageLogin1'></div>"
			+ "<div class='auth-actions'><button type='submit'>" + escapeHtml(translate("LOGIN_LABEL")) + "</button></div>"
			+ "</form>"

			+ "<form class='auth-card auth-form auth-hidden' data-card='passwordExpired' data-submit-action='passwordExpired'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("NEW_PASSWORD_LABEL")) + "</label><input type='password' name='password' required></div>"
			+ renderExtraFields(authConfig.extraLoginStep2Fields)
			+ "<div class='auth-message' data-message='messagePasswordExpired'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='backToAuthenticate'>" + escapeHtml(translate("BACK_BUTTON")) + "</button><button type='submit'>" + escapeHtml(translate("CHANGE_PASSWORD_BUTTON")) + "</button></div>"
			+ "</form>"

			+ "<form class='auth-card auth-form auth-hidden' data-card='totpToken' data-submit-action='totpToken'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("TWO_FACTOR_LABEL")) + "</label><input type='text' name='totpToken' required></div>"
			+ "<div class='auth-message' data-message='messageTwoFactorToken'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='backToAuthenticate'>" + escapeHtml(translate("BACK_BUTTON")) + "</button><button type='submit'>" + escapeHtml(translate("SUBMIT")) + "</button></div>"
			+ "</form>"

			+ "<form class='auth-card auth-form auth-hidden' data-card='totpSetup' data-submit-action='totpSetupVerify'>"
			+ "<div class='auth-totp-qr'><img data-totp='qr-image' alt='TOTP QR code'></div>"
			+ "<div class='auth-field-row auth-hidden' data-totp='secret-row'><label>" + escapeHtml(translate("TWO_FACTOR_SECRET_LABEL")) + "</label><input type='text' data-totp='secret-input' readonly></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='showTotpSecret'>" + escapeHtml(translate("SHOW_SECRET_BUTTON")) + "</button></div>"
			+ "<div class='auth-help-label'>" + translate("TWO_FACTOR_SETUP_INSTRUCTIONS") + "</div>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("TWO_FACTOR_LABEL")) + "</label><input type='text' name='totpToken' required></div>"
			+ "<div class='auth-message' data-message='messageTwoFactorSetup'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='backToAuthenticate'>" + escapeHtml(translate("BACK_BUTTON")) + "</button><button type='button' data-click-action='totpDisable'>" + escapeHtml(translate("CANCEL_TOTP")) + "</button><button type='button' data-click-action='totpLoadSecret'>" + escapeHtml(translate("RELOAD")) + "</button><button type='submit'>" + escapeHtml(translate("SUBMIT")) + "</button></div>"
			+ "</form>"

			+ "<div class='auth-card auth-form auth-hidden' data-card='totpBackupCodes'>"
			+ "<div class='auth-field-row'><textarea data-totp='backup-codes' readonly></textarea></div>"
			+ "<div class='auth-help-label'>" + translate("TWO_FACTOR_BACKUP_CODES_INSTRUCTIONS") + "</div>"
			+ "<div class='auth-message' data-message='messageTwoFactorBackupCodes'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='totpBackupCodesPrint'>" + escapeHtml(translate("PRINT")) + "</button><button type='button' data-click-action='reloadPage'>" + escapeHtml(translate("OK")) + "</button></div>"
			+ "</div>"
			+ "</div>";
	};

	const buildRegisterTab = function() {
		if (!authConfig.showRegister) {
			return "";
		}

		if (authConfig.directRegistration) {
			return ""
				+ "<form class='auth-form' data-submit-action='directRegister'>"
				+ "<div class='auth-field-row'><label>" + escapeHtml(translate("EMAIL_LABEL")) + "</label><input type='email' name='email' required></div>"
				+ "<div class='auth-field-row'><label>" + escapeHtml(translate("PASSWORD_LABEL")) + "</label><input type='password' name='password' required></div>"
				+ renderExtraFields(authConfig.extraRegisterStep1Fields)
				+ "<div class='auth-message' data-message='messageRegister1'></div>"
				+ "<div class='auth-actions auth-actions-right'><button type='submit'>" + escapeHtml(translate("CREATE_ACCOUNT_BUTTON")) + "</button></div>"
				+ "</form>";
		}

		return ""
			+ "<div class='auth-card-container' data-card-container='register'>"
			+ "<form class='auth-card auth-form' data-card='register' data-submit-action='register'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("EMAIL_LABEL")) + "</label><input type='email' name='email' required></div>"
			+ renderExtraFields(authConfig.extraRegisterStep1Fields)
			+ "<div class='auth-message' data-message='messageRegister1'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='registerForward'>" + escapeHtml(translate("EXISTING_KEY_BUTTON")) + "</button><span class='auth-spacer'></span><button type='submit'>" + escapeHtml(translate("GENERATE_KEY_BUTTON")) + "</button></div>"
			+ "</form>"
			+ "<form class='auth-card auth-form auth-hidden' data-card='registerResetPassword' data-submit-action='registerResetPassword'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("ACTIVATION_KEY_LABEL")) + "</label><input type='text' name='activationKey' required></div>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("PASSWORD_LABEL")) + "</label><input type='password' name='password' required></div>"
			+ renderExtraFields(authConfig.extraRegisterStep2Fields)
			+ "<div class='auth-message' data-message='messageRegister2'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='registerBack'>" + escapeHtml(translate("BACK_BUTTON")) + "</button><button type='submit'>" + escapeHtml(translate("CREATE_ACCOUNT_BUTTON")) + "</button></div>"
			+ "</form>"
			+ "</div>";
	};

	const buildForgotPasswordTab = function() {
		if (!isVisible(authConfig.showForgotPassword)) {
			return "";
		}

		return ""
			+ "<div class='auth-card-container' data-card-container='forgotPassword'>"
			+ "<form class='auth-card auth-form' data-card='forgotPasswordRequest' data-submit-action='forgotPassword'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("IDENTIFIER_LABEL")) + "</label><input type='text' name='identifier' required></div>"
			+ renderExtraFields(authConfig.extraforgotPasswordStep1PanelFields)
			+ "<div class='auth-message' data-message='messageForgotPassword1'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='forgotPasswordForward'>" + escapeHtml(translate("EXISTING_KEY_BUTTON")) + "</button><span class='auth-spacer'></span><button type='submit'>" + escapeHtml(translate("GENERATE_KEY_BUTTON")) + "</button></div>"
			+ "</form>"
			+ "<form class='auth-card auth-form auth-hidden' data-card='forgotPasswordReset' data-submit-action='forgotPasswordReset'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("ACTIVATION_KEY_LABEL")) + "</label><input type='text' name='activationKey' required></div>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("NEW_PASSWORD_LABEL")) + "</label><input type='password' name='password' required></div>"
			+ renderExtraFields(authConfig.extraforgotPasswordStep2PanelFields)
			+ "<div class='auth-message' data-message='messageForgotPassword2'></div>"
			+ "<div class='auth-actions'><button type='button' data-click-action='forgotPasswordBack'>" + escapeHtml(translate("BACK_BUTTON")) + "</button><button type='submit'>" + escapeHtml(translate("RESET_PASSWORD_BUTTON")) + "</button></div>"
			+ "</form>"
			+ "</div>";
	};

	const buildForgotUsernameTab = function() {
		if (!authConfig.showForgotUsername) {
			return "";
		}

		return ""
			+ "<form class='auth-form' data-submit-action='forgotUsername'>"
			+ "<div class='auth-field-row'><label>" + escapeHtml(translate("EMAIL_LABEL")) + "</label><input type='email' name='email' required></div>"
			+ renderExtraFields(authConfig.extraForgotUsernameStep1PanelFields)
			+ "<div class='auth-message' data-message='messageForgotUsername1'></div>"
			+ "<div class='auth-actions auth-actions-right'><button type='submit'>" + escapeHtml(translate("SUBMIT")) + "</button></div>"
			+ "</form>";
	};

	const render = function(root) {
		const tabs = [];
		if (isVisible(authConfig.showLogin)) {
			tabs.push({ id: "login", title: translate("LOGIN_TITLE"), body: buildLoginTab() });
		}
		if (authConfig.showRegister) {
			tabs.push({ id: "register", title: translate("REGISTER_TITLE"), body: buildRegisterTab() });
		}
		if (isVisible(authConfig.showForgotPassword)) {
			tabs.push({ id: "forgotPassword", title: translate("RESET_TITLE"), body: buildForgotPasswordTab() });
		}
		if (authConfig.showForgotUsername) {
			tabs.push({ id: "forgotUsername", title: translate("FORGOT_USERNAME_TITLE"), body: buildForgotUsernameTab() });
		}

		if (tabs.length === 0) {
			root.innerHTML = "<div class='auth-panel'><div class='auth-help-label'>" + escapeHtml(translate("FORM_TITLE")) + "</div></div>";
			return;
		}

		const buttons = tabs.map(function(tab) {
			return "<button type='button' class='auth-tab-button' data-tab-target='" + tab.id + "'>" + escapeHtml(tab.title) + "</button>";
		}).join("");
		const panels = tabs.map(function(tab) {
			return "<div class='auth-tab-panel auth-hidden' data-tab-panel='" + tab.id + "'>" + tab.body + "</div>";
		}).join("");

		root.innerHTML = ""
			+ "<div class='auth-panel'>"
			+ "<div class='auth-tab-bar'>" + buttons + "</div>"
			+ panels
			+ "</div>";

		const initialTab = tabs[0].id;
		activateTab(root, initialTab);

		if (isVisible(authConfig.showLogin)) {
			const activeLoginCard = authConfig.activeItem || "authenticate";
			activateCard(root, "login", activeLoginCard);
			if (activeLoginCard !== "authenticate") {
				activateTab(root, "login");
			}
			if (activeLoginCard === "totpSetup") {
				loadTotpSecret(root);
			}
			if (activeLoginCard === "totpBackupCodes") {
				loadTotpBackupCodes(root);
			}
		}
	};

	const setButtonLoading = function(button, loading) {
		if (!button) {
			return;
		}
		button.disabled = loading;
		button.classList.toggle("auth-loading", loading);
	};

	const withSubmitButton = async function(form, fn) {
		const submitButton = form.querySelector("button[type='submit']");
		setButtonLoading(submitButton, true);
		try {
			await fn();
		}
		finally {
			setButtonLoading(submitButton, false);
		}
	};

	const wireEvents = function(root) {
		root.addEventListener("click", async function(event) {
			const tabButton = event.target.closest("[data-tab-target]");
			if (tabButton) {
				activateTab(root, tabButton.getAttribute("data-tab-target"));
				return;
			}

			const actionButton = event.target.closest("[data-click-action]");
			if (!actionButton) {
				return;
			}

			const action = actionButton.getAttribute("data-click-action");
			if (action === "backToAuthenticate") {
				activateCard(root, "login", "authenticate");
				return;
			}
			if (action === "registerForward") {
				activateCard(root, "register", "registerResetPassword");
				return;
			}
			if (action === "registerBack") {
				activateCard(root, "register", "register");
				return;
			}
			if (action === "forgotPasswordForward") {
				activateCard(root, "forgotPassword", "forgotPasswordReset");
				return;
			}
			if (action === "forgotPasswordBack") {
				activateCard(root, "forgotPassword", "forgotPasswordRequest");
				return;
			}
			if (action === "showTotpSecret") {
				const secretRow = root.querySelector("[data-totp='secret-row']");
				if (secretRow) {
					secretRow.classList.remove("auth-hidden");
				}
				actionButton.classList.add("auth-hidden");
				return;
			}
			if (action === "totpLoadSecret") {
				await loadTotpSecret(root);
				return;
			}
			if (action === "totpDisable") {
				try {
					await request("authentication/totpSetup", "DELETE", null);
				}
				catch (e) {
					// Ignore.
				}
				window.location.reload();
				return;
			}
			if (action === "totpBackupCodesPrint") {
				const backupCodes = root.querySelector("[data-totp='backup-codes']");
				const popup = window.open("", "_blank");
				if (popup && backupCodes) {
					popup.document.write("<html><head><script type='text/javascript'>setTimeout(function(){window.print();},100);</script></head><body><pre>" + escapeHtml(backupCodes.value) + "</pre></body></html>");
					popup.document.close();
					popup.focus();
				}
				return;
			}
			if (action === "reloadPage") {
				window.location.reload();
			}
		});

		root.addEventListener("submit", async function(event) {
			const form = event.target.closest("form[data-submit-action]");
			if (!form) {
				return;
			}
			event.preventDefault();

			const submitAction = form.getAttribute("data-submit-action");
			if (!submitAction) {
				return;
			}

			if (!form.reportValidity()) {
				return;
			}

			await withSubmitButton(form, async function() {
				if (submitAction === "authenticate") {
					try {
						const result = await request("authentication/login", "POST", toUrlSearchParams(form));
						const response = result.json || {};
						if (result.ok && response.success === true) {
							window.location.reload();
							return;
						}
						if (response.next === "passwordExpired") {
							activateCard(root, "login", "passwordExpired");
							showMessage(root, "messagePasswordExpired", translate("FORCED_PASSWORD_CHANGE_MESSAGE"), 30000);
							return;
						}
						if (response.next === "totpToken") {
							activateCard(root, "login", "totpToken");
							showMessage(root, "messageTwoFactorToken", translate("TWO_FACTOR_MESSAGE"), 30000);
							return;
						}
						if (response.next === "totpSetup") {
							activateCard(root, "login", "totpSetup");
							await loadTotpSecret(root);
							return;
						}
						if (response.next === "totpBackupCodes") {
							activateCard(root, "login", "totpBackupCodes");
							await loadTotpBackupCodes(root);
							return;
						}
						showMessage(root, "messageLogin1", translate("INVALID_CREDENTIALS_MESSAGE"));
					}
					catch (e) {
						showMessage(root, "messageLogin1", translate("UNKNOWN_ERROR_MESSAGE"));
					}
					return;
				}

				if (submitAction === "passwordExpired") {
					try {
						const result = await request("authentication/passwordExpired", "POST", toUrlSearchParams(form));
						if (result.ok) {
							window.location.reload();
							return;
						}
					}
					catch (e) {
						// Handled below.
					}
					showMessage(root, "messagePasswordExpired", translate("FORCED_PASSWORD_CHANGE_ERROR_MESSAGE"));
					return;
				}

				if (submitAction === "totpToken") {
					try {
						const result = await request("authentication/totpToken", "POST", toUrlSearchParams(form));
						const response = result.json || {};
						if (result.ok && response.success === true) {
							window.location.reload();
							return;
						}
						if (response.next === "totpBackupCodesNeeded") {
							activateCard(root, "login", "totpBackupCodes");
							await loadTotpBackupCodes(root);
							return;
						}
						if (response.next === "passwordExpired") {
							activateCard(root, "login", "passwordExpired");
							showMessage(root, "messagePasswordExpired", translate("FORCED_PASSWORD_CHANGE_MESSAGE"), 30000);
							return;
						}
					}
					catch (e) {
						// Handled below.
					}
					showMessage(root, "messageTwoFactorToken", translate("INVALID_TWO_FACTOR_MESSAGE"));
					return;
				}

				if (submitAction === "totpSetupVerify") {
					try {
						const result = await request("authentication/totpSetup", "POST", toUrlSearchParams(form));
						if (result.ok) {
							activateCard(root, "login", "totpBackupCodes");
							await loadTotpBackupCodes(root);
							return;
						}
					}
					catch (e) {
						// Handled below.
					}
					showMessage(root, "messageTwoFactorSetup", translate("INVALID_TWO_FACTOR_MESSAGE"));
					return;
				}

				if (submitAction === "directRegister") {
					try {
						const result = await request("authentication/register", "POST", toUrlSearchParams(form));
						if (result.ok && result.json && result.json.success === true) {
							window.location.reload();
							return;
						}
						showMessage(root, "messageRegister1", getResponseMessage(result));
					}
					catch (e) {
						showMessage(root, "messageRegister1", translate("UNKNOWN_ERROR_MESSAGE"));
					}
					return;
				}

				if (submitAction === "register") {
					try {
						const result = await request("authentication/register", "POST", toUrlSearchParams(form));
						if (result.ok && result.json && result.json.success === true) {
							activateCard(root, "register", "registerResetPassword");
							showMessage(root, "messageRegister2", translate("ACTIVATION_KEY_SENT"), 30000);
							return;
						}
						showMessage(root, "messageRegister1", getResponseMessage(result));
					}
					catch (e) {
						showMessage(root, "messageRegister1", translate("UNKNOWN_ERROR_MESSAGE"));
					}
					return;
				}

				if (submitAction === "registerResetPassword") {
					try {
						const result = await request("authentication/resetPassword", "POST", toUrlSearchParams(form));
						if (result.ok && result.json && result.json.success === true) {
							window.location.reload();
							return;
						}
					}
					catch (e) {
						// Handled below.
					}
					showMessage(root, "messageRegister2", translate("UNKNOWN_ERROR_MESSAGE"));
					return;
				}

				if (submitAction === "forgotPassword") {
					try {
						const result = await request("authentication/forgotPassword", "POST", toUrlSearchParams(form));
						if (result.ok || result.status === 204) {
							activateCard(root, "forgotPassword", "forgotPasswordReset");
							showMessage(root, "messageForgotPassword2", translate("ACTIVATION_KEY_SENT"), 30000);
							return;
						}
					}
					catch (e) {
						// Handled below.
					}
					showMessage(root, "messageForgotPassword1", translate("UNKNOWN_ERROR_MESSAGE"));
					return;
				}

				if (submitAction === "forgotPasswordReset") {
					try {
						const result = await request("authentication/resetPassword", "POST", toUrlSearchParams(form));
						if (result.ok && result.json && result.json.success === true) {
							window.location.reload();
							return;
						}
					}
					catch (e) {
						// Handled below.
					}
					showMessage(root, "messageForgotPassword2", translate("UNKNOWN_ERROR_MESSAGE"));
					return;
				}

				if (submitAction === "forgotUsername") {
					try {
						const result = await request("authentication/forgotUsername", "POST", toUrlSearchParams(form));
						if (result.ok || result.status === 204) {
							showMessage(root, "messageForgotUsername1", translate("USER_NAMES_SENT"), 30000);
							return;
						}
					}
					catch (e) {
						// Handled below.
					}
					showMessage(root, "messageForgotUsername1", translate("UNKNOWN_ERROR_MESSAGE"));
				}
			});
		});
	};

	const promptCookieConsent = function() {
		if (!authConfig.showCookieWarning) {
			return;
		}

		if (window.navigator && window.navigator.webdriver === true) {
			try {
				window.localStorage.setItem("allowCookies:" + window.location.href, "true");
			}
			catch (e) {
				// Ignore storage errors.
			}
			return;
		}

		let storageAllowed = false;
		try {
			storageAllowed = window.localStorage.getItem("allowCookies:" + window.location.href) === "true";
		}
		catch (e) {
			storageAllowed = false;
		}

		if (storageAllowed) {
			return;
		}

		const accepted = window.confirm(translate("COOKIES_USED_TITLE") + "\n\n" + translate("COOKIES_USED_MESSAGE"));
		if (accepted) {
			try {
				window.localStorage.setItem("allowCookies:" + window.location.href, "true");
			}
			catch (e) {
				// Ignore storage errors.
			}
			return;
		}

		setTimeout(promptCookieConsent, 10);
	};

	document.addEventListener("DOMContentLoaded", function() {
		const root = document.getElementById("loginform");
		if (!root) {
			return;
		}

		render(root);
		wireEvents(root);
		loadStoreOptions("locales", "stores/locales", 0);
		loadStoreOptions("currencies", "stores/currencies", 0);
		promptCookieConsent();
	});
})();
