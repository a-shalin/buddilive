package ca.digitalcave.buddi.live.db;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import ca.digitalcave.buddi.live.model.Entry;
import ca.digitalcave.buddi.live.model.User;


@Mapper
public interface Entries {
	List<Entry> selectEntries(@Param("user") User user);
	
	@MapKey("categoryId")
	Map<Integer, Entry> selectEntries(@Param("user") User user, @Param("date") Date date);
	
	@MapKey("date")
	Map<Date, Entry> selectEntries(@Param("user") User user, @Param("categoryId") Integer categoryId);
	
	Entry selectEntry(@Param("user") User user, @Param("id") Long id);
	Entry selectEntry(@Param("user") User user, @Param("entry") Entry entry);	//date and category ID must be populated
	Entry selectEntry(@Param("user") User user, @Param("date") Date date, @Param("categoryId") Integer categoryId);
	
	Integer insertEntry(@Param("user") User user, @Param("entry") Entry entry);
	
	Integer updateEntry(@Param("user") User user, @Param("entry") Entry entry);
	
//	Integer copyFromPreviousPeriod(@Param("user") User user, @Param("previousDate") Date previousDate, @Param("currentDate") Date currentDate);
}
