package ca.digitalcave.buddi.live.db;

import ca.digitalcave.buddi.live.model.Source;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

import java.util.Date;
import java.util.List;


@Mapper
public interface Transactions {
	int selectTransactionCount(@Param("user") User user, @Param("uuid") String uuid);
	
	List<Transaction> selectTransactions(@Param("user") User user);
	List<Transaction> selectTransactions(@Param("user") User user, @Param("fromDate") Date fromDate, @Param("toDate") Date toDate);
	List<Transaction> selectTransactions(@Param("user") User user, @Param("type") String type, @Param("fromDate") Date fromDate, @Param("toDate") Date toDate);
	List<Transaction> selectTransactions(@Param("user") User user, @Param("source") Source source, @Param("fromDate") Date fromDate, @Param("toDate") Date toDate);
	void selectTransactions(@Param("user") User user, @Param("source") Source source, ResultHandler<Transaction> handler);
	
	List<Transaction> selectDescriptions(@Param("user") User user);
	
	List<Split> selectSplits(@Param("user") User user);
	
	Integer insertTransaction(@Param("user") User user, @Param("transaction") Transaction transaction);
	Integer insertSplit(@Param("user") User user, @Param("split") Split split);
	
	Integer updateTransaction(@Param("user") User user, @Param("transaction") Transaction transaction);
	Integer updateSplit(@Param("user") User user, @Param("split") Split split);
	Integer updateSplitBalance(@Param("user") User user, @Param("splitId") long splitId, @Param("balance") String balance, @Param("from") boolean updateFromBalance);
	
	Integer deleteAllTransactions(@Param("user") User user);
	Integer deleteTransaction(@Param("user") User user, @Param("transaction") Transaction transaction);
	Integer deleteSplits(@Param("user") User user, @Param("transaction") Transaction transaction);
}
