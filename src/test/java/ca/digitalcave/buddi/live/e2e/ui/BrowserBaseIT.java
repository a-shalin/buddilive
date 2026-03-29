package ca.digitalcave.buddi.live.e2e.ui;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import ca.digitalcave.buddi.live.e2e.BaseIT;
import ca.digitalcave.buddi.live.e2e.TestHelper;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BrowserBaseIT extends BaseIT {

	protected WebDriver driver;
	protected WebDriverWait wait;
	protected TestHelper helper;

	@BeforeEach
	void setUpBrowser() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");
		options.addArguments("--window-size=1280,1024");
		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(15));
		helper = new TestHelper(getBaseUrl(), getDbUrl());
	}

	@AfterEach
	void tearDownBrowser() {
		if (driver != null) {
			driver.quit();
		}
	}

	protected Object executeJs(String script, Object... args) {
		return ((JavascriptExecutor) driver).executeScript(script, args);
	}

	protected void installJsErrorCollector() {
		executeJs(
			"window.__e2eJsErrors = [];" +
			"if (!window.__e2eJsCollectorInstalled) {" +
			"  window.__e2eJsCollectorInstalled = true;" +
			"  window.addEventListener('error', function(event) {" +
			"    var message = (event && event.message) ? event.message : 'unknown error';" +
			"    window.__e2eJsErrors.push(message);" +
			"  });" +
			"  window.addEventListener('unhandledrejection', function(event) {" +
			"    var reason = (event && event.reason) ? (event.reason.message || String(event.reason)) : 'unknown rejection';" +
			"    window.__e2eJsErrors.push('unhandledrejection: ' + reason);" +
			"  });" +
			"}");
	}

	protected void assertNoJsErrors() {
		final Object result = executeJs("return (window.__e2eJsErrors || []).slice();");
		assertThat(result).isInstanceOf(List.class);
		final List<?> errors = (List<?>) result;
		assertThat(errors).as("Unexpected browser JavaScript errors").isEmpty();
	}

	protected void waitForExtJs() {
		wait.until(d -> {
			try {
				return (Boolean) executeJs("return typeof Ext !== 'undefined' && Ext.isReady === true;");
			} catch (Exception e) {
				return false;
			}
		});
	}

	protected void waitForComponent(String query) {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"var cmp = Ext.ComponentQuery.query(arguments[0])[0]; return cmp != null && cmp.rendered === true;",
					query);
			} catch (Exception e) {
				return false;
			}
		});
	}

	protected void setExtFieldValue(String query, String value) {
		executeJs(
			"Ext.ComponentQuery.query(arguments[0])[0].setValue(arguments[1]);",
			query, value);
	}

	protected void clickExtButton(String query) {
		executeJs(
			"var btn = Ext.ComponentQuery.query(arguments[0])[0]; btn.getEl().dom.click();",
			query);
	}

	protected String getExtFieldValue(String query) {
		Object result = executeJs(
			"var cmp = Ext.ComponentQuery.query(arguments[0])[0]; return cmp ? String(cmp.getValue()) : null;",
			query);
		return result != null ? result.toString() : null;
	}

	protected void assertGridRowsVisible(String gridQuery) {
		final Boolean visible = (Boolean) executeJs(
			"var grid = Ext.ComponentQuery.query(arguments[0])[0];" +
			"var view = grid.getView();" +
			"var items = grid.getEl().query('.x-grid-item');" +
			"if (items.length === 0) return false;" +
			"var gridRect = grid.getEl().dom.getBoundingClientRect();" +
			"return items.some(function(el) {" +
			"  var r = el.getBoundingClientRect();" +
			"  return r.width > 0 && r.bottom > gridRect.top && r.top < gridRect.bottom;" +
			"});",
			gridQuery);
		assertThat(visible).as("Grid rows in " + gridQuery + " should be visible in viewport").isTrue();
	}

	protected void waitForStoreLoad(String gridQuery) {
		executeJs(
			"window.__storeLoaded = false;" +
			"var list = Ext.ComponentQuery.query(arguments[0])[0];" +
			"list.getStore().on('load', function() { window.__storeLoaded = true; }, null, {single: true});",
			gridQuery);
		wait.until(d -> {
			try {
				return (Boolean) executeJs("return window.__storeLoaded === true;");
			} catch (Exception e) {
				return false;
			}
		});
	}

	protected void dismissMessageBox() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		executeJs(
			"if (Ext.MessageBox && Ext.MessageBox.isVisible()) { Ext.MessageBox.hide(); }");
	}

	protected void waitForAppReload() {
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"return typeof BuddiLive !== 'undefined' && BuddiLive.app != null && BuddiLive.app.viewport != null;");
			} catch (Exception e) {
				return false;
			}
		});
		waitForComponent("accounttree");
	}

	protected void browserLogin(String email, String password) {
		driver.get(getBaseUrl() + "/");
		waitForExtJs();
		waitForComponent("login");

		setExtFieldValue("login textfield[name=identifier]", email);
		setExtFieldValue("login textfield[name=password]", password);
		clickExtButton("login button[itemId=authenticate]");

		// Login triggers page reload; wait for main app viewport
		wait.until(d -> {
			try {
				return (Boolean) executeJs(
					"return typeof BuddiLive !== 'undefined' && BuddiLive.app != null && BuddiLive.app.viewport != null;");
			} catch (Exception e) {
				return false;
			}
		});
		waitForComponent("accounttree");
		dismissMessageBox();
	}
}
