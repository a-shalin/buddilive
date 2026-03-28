package ca.digitalcave.buddi.live.db;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.User;


@Mapper
public interface ScheduledTransactions {

	/**
	 * Returns all scheduled transactions who meet the following criteria: 
	 * 
	 * 1) Start date is before today
	 * 2) Last date created is before today (or is not yet set).
	 * 3) End date is after today (or is not defined)
	 * 
	 * This is used at when determining which scheduled transactions we need to check and (possibly) add.
	 */
	List<ScheduledTransaction> selectOustandingScheduledTransactions(@Param("user") User user);
	
	List<ScheduledTransaction> selectScheduledTransactions(@Param("user") User user);
	
	int selectScheduledTransactionCount(@Param("user") User user, @Param("uuid") String uuid);
	
	Integer insertScheduledTransaction(@Param("user") User user, @Param("transaction") ScheduledTransaction transaction);
	Integer insertScheduledSplit(@Param("user") User user, @Param("split") Split split);
	
	Integer updateScheduledTransaction(@Param("user") User user, @Param("transaction") ScheduledTransaction transaction);
	Integer updateScheduledSplit(@Param("user") User user, @Param("split") Split split);
	
	Integer deleteAllScheduledTransactions(@Param("user") User user);
	Integer deleteScheduledTransaction(@Param("user") User user, @Param("transaction") ScheduledTransaction transaction);
	Integer deleteScheduledSplits(@Param("user") User user, @Param("transaction") ScheduledTransaction transaction);
	Integer deleteScheduledSplit(@Param("user") User user, @Param("split") Split split);
}
