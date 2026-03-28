package ca.digitalcave.buddi.live.db.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;

public class CurrencyHandler extends BaseTypeHandler<Currency> {
	@Override
	public Currency getNullableResult(final ResultSet rs, final String columnName) throws SQLException {
		final String rawValue = rs.getString(columnName);
		return Currency.getInstance(rawValue);
	}
	
	@Override
	public Currency getNullableResult(final CallableStatement cs, final int columnIndex) throws SQLException {
		final String rawValue = cs.getString(columnIndex);
		return Currency.getInstance(rawValue);
	}
	
	@Override
	public void setNonNullParameter(final PreparedStatement ps, final int i, final Currency parameter, final JdbcType jdbcType) throws SQLException {
		ps.setString(i, parameter.getCurrencyCode());
	}
	
	@Override
	public Currency getNullableResult(final ResultSet rs, final int columnIndex) throws SQLException {
		final String rawValue = rs.getString(columnIndex);
		return Currency.getInstance(rawValue);
	}
}
