package ca.digitalcave.buddi.live.security;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class BuddiLiveAuthenticationHelperTest {

	@Test
	public void showsDisableIpLockOptionWhenGlobalDisableIsOff() {
		final BuddiLiveAuthenticationTransactionalService txService = mock(BuddiLiveAuthenticationTransactionalService.class);
		final Properties mailProperties = new Properties();
		final BuddiLiveAuthenticationHelper helper = new BuddiLiveAuthenticationHelper(txService, mailProperties, false, false);

		assertThat(helper.getConfig().showDisableIpLock).isTrue();
	}

	@Test
	public void hidesDisableIpLockOptionWhenGlobalDisableIsOn() {
		final BuddiLiveAuthenticationTransactionalService txService = mock(BuddiLiveAuthenticationTransactionalService.class);
		final Properties mailProperties = new Properties();
		final BuddiLiveAuthenticationHelper helper = new BuddiLiveAuthenticationHelper(txService, mailProperties, false, true);

		assertThat(helper.getConfig().showDisableIpLock).isFalse();
	}
}
