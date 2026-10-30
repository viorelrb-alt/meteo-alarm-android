package ro.meteoalarm.android.core;

import java.util.Calendar;

/** The interface texts in Romanian and English (the Kindle's T_* strings). */
public final class Texts {
	public final boolean en;
	public String until, now, rain, rainFrom, max, snow, snowFrom, rainW, snowW, updated, noData, loading, updating;
	public String feels, wind, hi, lo, charging, warnCode, day1, day2, next12, nowMark, rainUp, snowUp;
	public String hoursUp, legSnow, legRain, legDry, pop, sun, uvT, pollen, sunrise, sunset, noPollen, noData2, noPrecip;
	public String[] warn, uvLv, polLv;

	public Texts(boolean en) {
		this.en = en;
		if (en) {
			until = "until"; now = "now"; rain = "Rain"; rainFrom = "Rain from"; max = "max"; snow = "Snow"; snowFrom = "Snow from";
			rainW = "Raining"; snowW = "Snowing"; updated = "updated"; noData = "no data since"; loading = "Loading weather…";
			updating = "updating…"; feels = "Feels like"; wind = "Wind"; hi = "High"; lo = "Low"; charging = "Charging";
			warn = new String[] { "YELLOW", "ORANGE", "RED" }; warnCode = "WARNING"; day1 = "TOMORROW"; day2 = "DAY AFTER";
			next12 = "NEXT 12 HOURS"; hoursUp = "WEATHER · NEXT HOURS"; nowMark = "now"; rainUp = "RAIN"; snowUp = "SNOW"; legSnow = "snow"; legRain = "rain";
			legDry = "no precipitation"; pop = "rain"; sun = "SUN"; uvT = "UV INDEX"; pollen = "POLLEN"; sunrise = "Sunrise";
			sunset = "Sunset"; uvLv = new String[] { "Low", "Moderate", "High", "Very high", "Extreme" };
			polLv = new String[] { "Low", "Moderate", "High", "Very high" }; noPollen = "no pollen"; noData2 = "no data";
			noPrecip = "No precipitation";
		} else {
			until = "până la"; now = "acum"; rain = "Ploaie"; rainFrom = "Ploaie de la"; max = "maxim"; snow = "Ninsoare";
			snowFrom = "Ninsoare de la"; rainW = "Plouă"; snowW = "Ninge"; updated = "actualizat"; noData = "fără date din";
			loading = "Se descarcă vremea…"; updating = "se actualizează…"; feels = "Resimțit"; wind = "Vânt"; hi = "Max";
			lo = "Min"; charging = "Se încarcă"; warn = new String[] { "GALBEN", "PORTOCALIU", "ROȘU" }; warnCode = "COD";
			day1 = "MÂINE"; day2 = "POIMÂINE"; next12 = "URMĂTOARELE 12 ORE"; hoursUp = "VREMEA · URMĂTOARELE ORE"; nowMark = "acum"; rainUp = "PLOAIE";
			snowUp = "NINSOARE"; legSnow = "ninsoare"; legRain = "ploaie"; legDry = "fără precipitații"; pop = "ploaie";
			sun = "SOARE"; uvT = "INDICE UV"; pollen = "POLEN"; sunrise = "Răsărit"; sunset = "Apus";
			uvLv = new String[] { "Scăzut", "Moderat", "Ridicat", "Foarte ridicat", "Extrem" };
			polLv = new String[] { "Scăzut", "Moderat", "Ridicat", "Foarte ridicat" }; noPollen = "fără polen"; noData2 = "fără date";
			noPrecip = "Fără precipitații";
		}
	}

	/** Icon name and description of a WMO code (day or night icon). */
	public String[] describe(int code, boolean day) {
		String sfx = day ? "day" : "night";
		String icon, ro, enD;
		switch (code) {
		case 0: icon = "clear-" + sfx; ro = "Senin"; enD = "Clear"; break;
		case 1: icon = "partly-" + sfx; ro = "Aproape senin"; enD = "Mostly clear"; break;
		case 2: icon = "partly-" + sfx; ro = "Parțial noros"; enD = "Partly cloudy"; break;
		case 3: icon = "cloudy"; ro = "Înnorat"; enD = "Overcast"; break;
		case 45: case 48: icon = "fog"; ro = "Ceață"; enD = "Fog"; break;
		case 51: case 53: case 55: icon = "drizzle"; ro = "Burniță"; enD = "Drizzle"; break;
		case 56: case 57: icon = "drizzle"; ro = "Burniță înghețată"; enD = "Freezing drizzle"; break;
		case 61: icon = "rain"; ro = "Ploaie slabă"; enD = "Light rain"; break;
		case 63: icon = "rain"; ro = "Ploaie"; enD = "Rain"; break;
		case 65: icon = "rain"; ro = "Ploaie intensă"; enD = "Heavy rain"; break;
		case 66: case 67: icon = "rain"; ro = "Ploaie înghețată"; enD = "Freezing rain"; break;
		case 71: icon = "snow"; ro = "Ninsoare slabă"; enD = "Light snow"; break;
		case 73: icon = "snow"; ro = "Ninsoare"; enD = "Snow"; break;
		case 75: icon = "snow"; ro = "Ninsoare intensă"; enD = "Heavy snow"; break;
		case 77: icon = "snow"; ro = "Zăpadă fină"; enD = "Snow grains"; break;
		case 80: icon = "rain"; ro = "Averse slabe"; enD = "Light showers"; break;
		case 81: icon = "rain"; ro = "Averse"; enD = "Showers"; break;
		case 82: icon = "rain"; ro = "Averse intense"; enD = "Heavy showers"; break;
		case 85: case 86: icon = "snow"; ro = "Averse de ninsoare"; enD = "Snow showers"; break;
		case 95: icon = "thunder"; ro = "Furtună"; enD = "Thunderstorm"; break;
		case 96: case 99: icon = "thunder"; ro = "Furtună cu grindină"; enD = "Thunderstorm, hail"; break;
		default: icon = "cloudy"; ro = "Cod " + code; enD = "Code " + code;
		}
		return new String[] { icon, en ? enD : ro };
	}

	private static final String[] WD_RO = { "Duminică", "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă" };
	private static final String[] WD_EN = { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
	private static final String[] WS_RO = { "DUM", "LUN", "MAR", "MIE", "JOI", "VIN", "SÂM" };
	private static final String[] WS_EN = { "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT" };
	private static final String[] WM_RO = { "Dum", "Lun", "Mar", "Mie", "Joi", "Vin", "Sâm" };
	private static final String[] WM_EN = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
	private static final String[] MO_RO = { "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie", "iulie", "august",
			"septembrie", "octombrie", "noiembrie", "decembrie" };
	private static final String[] MO_EN = { "January", "February", "March", "April", "May", "June", "July", "August",
			"September", "October", "November", "December" };

	/** "Joi, 29 octombrie" / "Thursday, October 29". */
	public String date(Calendar c) {
		int wd = c.get(Calendar.DAY_OF_WEEK) - 1, d = c.get(Calendar.DAY_OF_MONTH), mo = c.get(Calendar.MONTH);
		return en ? WD_EN[wd] + ", " + MO_EN[mo] + " " + d : WD_RO[wd] + ", " + d + " " + MO_RO[mo];
	}

	/** Weekday in capitals of an ISO date ("2026-10-30" -> "VIN"); with DAY, followed by the day of the month. */
	public String weekdayUp(String iso, boolean withDay) {
		Calendar c = Calendar.getInstance();
		c.clear();
		int d = Integer.parseInt(iso.substring(8, 10));
		c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1, d);
		String w = (en ? WS_EN : WS_RO)[c.get(Calendar.DAY_OF_WEEK) - 1];
		return withDay ? w + " " + d : w;
	}

	/** "Lun 09:40" for an epoch time. */
	public String weekdayTime(long ms, boolean clk12) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(ms);
		return (en ? WM_EN : WM_RO)[c.get(Calendar.DAY_OF_WEEK) - 1] + " "
				+ clock(String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), clk12);
	}

	/** "07:42" or "7:42 AM". */
	public static String clock(String hhmm, boolean clk12) {
		if (!clk12 || hhmm == null || hhmm.length() < 5) return hhmm == null || hhmm.length() == 0 ? "–" : hhmm;
		int h = Integer.parseInt(hhmm.substring(0, 2));
		String s = h < 12 ? "AM" : "PM";
		h %= 12;
		if (h == 0) h = 12;
		return h + hhmm.substring(2, 5) + " " + s;
	}

	/** Warning types in capitals ("wind" -> "VÂNT"). */
	public String warnType(String types) {
		StringBuilder b = new StringBuilder();
		for (String w : types.split(",")) {
			w = w.trim();
			String u;
			if (en) u = w.replace('-', ' ').toUpperCase();
			else if (w.equals("wind")) u = "VÂNT";
			else if (w.startsWith("snow")) u = "NINSOARE, POLEI";
			else if (w.startsWith("thunderstorm")) u = "FURTUNI";
			else if (w.equals("fog")) u = "CEAȚĂ";
			else if (w.startsWith("high-temperature")) u = "CANICULĂ";
			else if (w.startsWith("low-temperature")) u = "GER";
			else if (w.startsWith("coastal")) u = "FENOMENE COSTIERE";
			else if (w.startsWith("forest-fire")) u = "RISC DE INCENDIU";
			else if (w.startsWith("avalanche")) u = "AVALANȘE";
			else if (w.startsWith("rain-flood")) u = "PLOI, INUNDAȚII";
			else if (w.startsWith("rain")) u = "PLOI";
			else if (w.startsWith("flood")) u = "INUNDAȚII";
			else u = w.toUpperCase();
			if (b.length() > 0) b.append(", ");
			b.append(u);
		}
		return b.toString();
	}

	public String plant(String p) {
		if (en) return p.equals("grass") ? "grasses" : p;
		if (p.equals("alder")) return "arin";
		if (p.equals("birch")) return "mesteacăn";
		if (p.equals("olive")) return "măslin";
		if (p.equals("grass")) return "graminee";
		if (p.equals("mugwort")) return "pelin";
		if (p.equals("ragweed")) return "ambrozie";
		return p;
	}
}
