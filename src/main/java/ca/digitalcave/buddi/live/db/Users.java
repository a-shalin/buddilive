package ca.digitalcave.buddi.live.db;

import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.auth.model.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface Users {
	User selectUser(@Param("identifier") String identifier);
	User selectUserByActivationKey(@Param("activationKey") String activationKey);
	int selectEncryptionVersion(@Param("user") User user);
	
	int insertUser(@Param("user") User user);
	int insertActivationKey(@Param("user") User user, @Param("activationKey") String activationKey);
	int insertTotpBackupCode(@Param("user") User user, @Param("backupCode") String backupCode);
	
	int updateUserLoginTime(@Param("user") User user);
	int updateUser(@Param("user") User user);
	int updateUserEncryptionKey(@Param("user") User user);
	int updateUserSecret(@Param("user") AuthUser user, @Param("hashedSecret") String hashedSecret);
	int updateUserEncryptionVersion(@Param("user") User user, @Param("encryptionVersion") int encryptionVersion);
	int updateUserPremium(@Param("user") User user, @Param("premium") String premium);
	int updateUserTotpSecret(@Param("user") User user, @Param("totpSecret") String totpSecret);
	int updateUserTotpBackupCodeUsed(@Param("user") User user, @Param("backupCode") String backupCode);
	
	int deleteUser(@Param("user") User user);
	int deleteActivationKey();
	int deleteActivationKey(@Param("user") User user);
	int deleteInactiveUsers();
	int deleteUnusedBackupCodes(@Param("user") User user);
}
