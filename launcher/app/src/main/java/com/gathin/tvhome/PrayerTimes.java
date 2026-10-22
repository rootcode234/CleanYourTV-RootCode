package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

public class PrayerTimes {

    public static class City {
        public final String id;
        public final String name;
        public final double lat;
        public final double lon;

        public City(String id, String name, double lat, double lon) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final City[] CITIES = {
        new City("blora", "Kab. Blora", -6.9698, 111.4184),
        new City("pati", "Kab. Pati", -6.7554, 111.0379),
        new City("rembang", "Kab. Rembang", -6.7063, 111.3414),
        new City("kudus", "Kab. Kudus", -6.8048, 110.8405),
        new City("grobogan", "Kab. Grobogan", -7.1086, 110.9169),
        new City("semarang", "Kota Semarang", -6.9667, 110.4167),
        new City("solo", "Kota Solo", -7.5755, 110.8243),
        new City("surabaya", "Kota Surabaya", -7.2575, 112.7521),
        new City("jakarta", "DKI Jakarta", -6.2088, 106.8456),
        new City("off", "Nonaktifkan", 0, 0)
    };

    public static City getCity(String id) {
        if (id == null) return CITIES[0];
        for (City c : CITIES) {
            if (c.id.equalsIgnoreCase(id)) return c;
        }
        return CITIES[0];
    }

    public static int getCityIndex(String id) {
        if (id == null) return 0;
        for (int i = 0; i < CITIES.length; i++) {
            if (CITIES[i].id.equalsIgnoreCase(id)) return i;
        }
        return 0;
    }

    public static class Schedule {
        public final String subuh;
        public final String dzuhur;
        public final String ashar;
        public final String maghrib;
        public final String isya;
        public final String nextPrayerName;
        public final String nextPrayerTime;
        public final String hijriDate;

        public Schedule(String subuh, String dzuhur, String ashar, String maghrib, String isya,
                        String nextPrayerName, String nextPrayerTime, String hijriDate) {
            this.subuh = subuh;
            this.dzuhur = dzuhur;
            this.ashar = ashar;
            this.maghrib = maghrib;
            this.isya = isya;
            this.nextPrayerName = nextPrayerName;
            this.nextPrayerTime = nextPrayerTime;
            this.hijriDate = hijriDate;
        }
    }

    public static Schedule calculate(City city, LocalDate date, LocalTime now) {
        if (city == null || "off".equalsIgnoreCase(city.id)) return null;

        int a = (14 - date.getMonthValue()) / 12;
        int y = date.getYear() + 4800 - a;
        int m = date.getMonthValue() + 12 * a - 3;
        double jd = date.getDayOfMonth() + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045 - 0.5;
        double d = jd - 2451545.0;

        double g = 357.529 + 0.98560028 * d;
        double q = 280.459 + 0.98564736 * d;
        double l = q + 1.915 * Math.sin(Math.toRadians(g)) + 0.020 * Math.sin(Math.toRadians(2 * g));
        double e = 23.439 - 0.00000036 * d;

        double ra = Math.toDegrees(Math.atan2(Math.cos(Math.toRadians(e)) * Math.sin(Math.toRadians(l)), Math.cos(Math.toRadians(l)))) / 15.0;
        ra = (ra % 24 + 24) % 24;
        double dec = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(e)) * Math.sin(Math.toRadians(l))));

        double eqt = q / 15.0 - ra;
        while (eqt < -12) eqt += 24;
        while (eqt > 12) eqt -= 24;

        double noon = 12.0 + 7.0 - city.lon / 15.0 - eqt; // WIB is UTC+7

        double haFajr = hourAngle(-20.0, city.lat, dec);
        double asrAlt = Math.toDegrees(Math.atan(1.0 / (1.0 + Math.tan(Math.toRadians(Math.abs(city.lat - dec))))));
        double haAsr = hourAngle(asrAlt, city.lat, dec);
        double haSunset = hourAngle(-0.833, city.lat, dec);
        double haIsha = hourAngle(-18.0, city.lat, dec);

        double ihtiyat = 2.0 / 60.0; // 2 minutes safety margin (standard Kemenag RI)
        double fFajr = noon - haFajr + ihtiyat;
        double fDhuhr = noon + ihtiyat;
        double fAsr = noon + haAsr + ihtiyat;
        double fMaghrib = noon + haSunset + ihtiyat;
        double fIsha = noon + haIsha + ihtiyat;

        String sSubuh = formatTime(fFajr);
        String sDzuhur = formatTime(fDhuhr);
        String sAshar = formatTime(fAsr);
        String sMaghrib = formatTime(fMaghrib);
        String sIsya = formatTime(fIsha);

        double nowHour = now.getHour() + now.getMinute() / 60.0;
        String nextName;
        String nextTime;
        if (nowHour < fFajr) {
            nextName = "Subuh";
            nextTime = sSubuh;
        } else if (nowHour < fDhuhr) {
            nextName = "Dzuhur";
            nextTime = sDzuhur;
        } else if (nowHour < fAsr) {
            nextName = "Ashar";
            nextTime = sAshar;
        } else if (nowHour < fMaghrib) {
            nextName = "Maghrib";
            nextTime = sMaghrib;
        } else if (nowHour < fIsha) {
            nextName = "Isya";
            nextTime = sIsya;
        } else {
            nextName = "Subuh (Besok)";
            nextTime = sSubuh;
        }

        boolean afterMaghrib = nowHour >= fMaghrib;
        String hijriDate = getHijriDate(date, afterMaghrib);

        return new Schedule(sSubuh, sDzuhur, sAshar, sMaghrib, sIsya, nextName, nextTime, hijriDate);
    }

    public static String getHijriDate(LocalDate date, boolean afterMaghrib) {
        if (afterMaghrib) {
            date = date.plusDays(1);
        }
        int gy = date.getYear();
        int gm = date.getMonthValue();
        int gd = date.getDayOfMonth();

        int a = (14 - gm) / 12;
        int y = gy + 4800 - a;
        int m = gm + 12 * a - 3;
        int jd = gd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;

        int l = jd - 1948440 + 10632;
        int n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        int j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int hm = (24 * l) / 709;
        int hd = l - (709 * hm) / 24;
        int hy = 30 * n + j - 30;

        String[] months = {
            "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir",
            "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban",
            "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
        };
        int monthIdx = Math.max(0, Math.min(11, hm - 1));
        return hd + " " + months[monthIdx] + " " + hy + " H";
    }

    private static double hourAngle(double alt, double lat, double dec) {
        double phi = Math.toRadians(lat);
        double delta = Math.toRadians(dec);
        double h0 = Math.toRadians(alt);
        double cosHa = (Math.sin(h0) - Math.sin(phi) * Math.sin(delta)) / (Math.cos(phi) * Math.cos(delta));
        if (cosHa < -1.0 || cosHa > 1.0) return 0.0;
        return Math.toDegrees(Math.acos(cosHa)) / 15.0;
    }

    private static String formatTime(double h) {
        int m = (int) Math.round(h * 60);
        int hr = (m / 60) % 24;
        int mn = m % 60;
        return String.format(Locale.US, "%02d:%02d", hr, mn);
    }
}
