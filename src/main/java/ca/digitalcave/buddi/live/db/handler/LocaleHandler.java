package ca.digitalcave.buddi.live.db.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import ca.digitalcave.buddi.live.util.LocaleUtil;

public class LocaleHandler extends BaseTypeHandler<Locale> {
	@Override
	public Locale getNullableResult(ResultSet rs, String columnName) throws SQLException {
		final String rawValue = rs.getString(columnName);
		return LocaleUtil.parseLocale(rawValue, Locale.US);
	}
	
	@Override
	public Locale getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		final String rawValue = cs.getString(columnIndex);
		return LocaleUtil.parseLocale(rawValue, Locale.US);
	}
	
	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, Locale parameter, JdbcType jdbcType) throws SQLException {
		ps.setString(i, LocaleUtil.parseLocale(parameter.toString(), parameter).toString());
	}
	
	@Override
	public Locale getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		final String rawValue = rs.getString(columnIndex);
		return LocaleUtil.parseLocale(rawValue, Locale.US);
	}
}
