package ca.digitalcave.buddi.live.db.handler;

import ca.digitalcave.buddi.live.util.LocaleUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class LocaleHandler extends BaseTypeHandler<Locale> {
	@Override
	public Locale getNullableResult(final ResultSet rs, final String columnName) throws SQLException {
		final String rawValue = rs.getString(columnName);
		return LocaleUtil.parseLocale(rawValue, Locale.US);
	}
	
	@Override
	public Locale getNullableResult(final CallableStatement cs, final int columnIndex) throws SQLException {
		final String rawValue = cs.getString(columnIndex);
		return LocaleUtil.parseLocale(rawValue, Locale.US);
	}
	
	@Override
	public void setNonNullParameter(final PreparedStatement ps, final int i, final Locale parameter, final JdbcType jdbcType) throws SQLException {
		ps.setString(i, LocaleUtil.parseLocale(parameter.toString(), parameter).toString());
	}
	
	@Override
	public Locale getNullableResult(final ResultSet rs, final int columnIndex) throws SQLException {
		final String rawValue = rs.getString(columnIndex);
		return LocaleUtil.parseLocale(rawValue, Locale.US);
	}
}
