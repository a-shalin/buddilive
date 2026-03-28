package ca.digitalcave.buddi.live.db.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BooleanHandler extends BaseTypeHandler<Boolean> {
	@Override
	public Boolean getNullableResult(final ResultSet rs, final String columnName) throws SQLException {
		final String rawValue = rs.getString(columnName);
		return "Y".equals(rawValue);
	}
	
	@Override
	public Boolean getNullableResult(final CallableStatement cs, final int columnIndex) throws SQLException {
		final String rawValue = cs.getString(columnIndex);
		return "Y".equals(rawValue);
	}
	
	@Override
	public void setNonNullParameter(final PreparedStatement ps, final int i, final Boolean parameter, final JdbcType jdbcType) throws SQLException {
		ps.setString(i, parameter ? "Y" : "N");
	}
	
	@Override
	public Boolean getNullableResult(final ResultSet rs, final int columnIndex) throws SQLException {
		final String rawValue = rs.getString(columnIndex);
		return "Y".equals(rawValue);
	}
}
