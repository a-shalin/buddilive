package ca.digitalcave.buddi.live.db;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BuddiSystem {
	String selectCookieEncryptionKey();
	int insertCookieEncryptionKey(@Param("encryptionKey") String encryptionKey);
	int updateCookieEncryptionKey(@Param("encryptionKey") String encryptionKey);
	int deleteCookieEncryptionKey();
}
