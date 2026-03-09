package ca.digitalcave.moss.crypto;

public class ObfuscateUtil {
	public static String obfuscate(String s) {
		if (s == null) return null;
		final StringBuilder sb = new StringBuilder();
		byte[] b = s.getBytes();

		sb.append("OBF:");
		for (int i = 0; i < b.length; i++) {
			final byte b1 = b[i];
			final byte b2 = b[s.length() - (i+1)];
			final int i1 = 127 + b1 + b2;
			final int i2 = 127 + b1 - b2;
			final int i0 = i1 * 256 + i2;
			final String x = Integer.toString(i0,36);

			switch (x.length()) {
			case 1:
			case 2:
			case 3:
				sb.append('0');
			default: 
				sb.append(x);
			}
		}
		return sb.toString();
	}

	public static String deobfuscate(String s) {
		if (s == null) return null;
		if (s.startsWith("OBF:")) {
			s = s.substring(4);
	
			final byte[] b = new byte[s.length()/2];
			int l = 0;
			for (int i = 0; i < s.length(); i += 4) {
				final String x = s.substring(i, i+4);
				final int i0 = Integer.parseInt(x, 36);
				final int i1 = (i0 / 256);
				final int i2 = (i0 % 256);
				b[l++] = (byte) ((i1 + i2 - 254) / 2);
			}
	
			return new String(b, 0, l);
		} else {
			return s;
		}
	}
}
