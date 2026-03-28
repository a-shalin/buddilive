package ca.digitalcave.buddi.live.db;

import ca.digitalcave.buddi.live.model.*;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface Sources {
	Account selectAccount(@Param("user") User user, @Param("uuid") String uuid);
	
	List<Account> selectAccountBalances(@Param("user") User user);

	List<Account> selectAccounts(@Param("user") User user);
	List<Account> selectAccounts(@Param("user") User user, @Param("accountType") String accountType);
	
	List<AccountType> selectAccountTypes(@Param("user") User user);
	
	Category selectCategory(@Param("user") User user, @Param("id") Integer id);
	Category selectCategory(@Param("user") User user, @Param("categoryPeriod") CategoryPeriod categoryPeriod, @Param("id") Integer id);
	Category selectCategory(@Param("user") User user, @Param("uuid") String uuid);
	
	List<Category> selectCategories(@Param("user") User user);
	List<Category> selectCategories(@Param("user") User user, @Param("income") Boolean income);
	List<Category> selectCategories(@Param("user") User user, @Param("periodType") String periodType);
	List<Category> selectCategories(@Param("user") User user, @Param("categoryPeriod") CategoryPeriod categoryPeriod);
	
	@MapKey("id")
	Map<Integer, Category> selectCategoriesMap(@Param("user") User user);
	
	List<String> selectCategoryPeriods(@Param("user") User user);
	
	Source selectSource(@Param("user") User user, @Param("id") int id);
	Integer selectSourceAssociatedCount(@Param("user") User user, @Param("source") Source source);
	
	Integer insertAccount(@Param("user") User user, @Param("account") Account account);
	Integer insertCategory(@Param("user") User user, @Param("category") Category category);
	
	Integer updateAccount(@Param("user") User user, @Param("account") Account account);
	Integer updateAccountBalance(@Param("user") User user, @Param("accountId") int accountId, @Param("balance") String balance);
	Integer updateCategory(@Param("user") User user, @Param("category") Category category);
	Integer updateSourceDeleted(@Param("user") User user, @Param("source") Source source);
	
	Integer deleteSource(@Param("user") User user, @Param("source") Source source);
	Integer deleteAllSources(@Param("user") User user);
}
