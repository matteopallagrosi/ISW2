package defectprediction;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;

public class Utils {

    //recupera l'oggetto json associato all'url indicato
    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        } finally {
            is.close();
        }
    }

    //estrae in forma testuale il contenuto di uno stream in lettura
    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    //converte unix time in localDateTime
    public static LocalDateTime convertTime(long unixSeconds) {
        /*// convert seconds to milliseconds
        Date date = new java.util.Date(unixSeconds*1000L);
        // the format of your date
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        // give a timezone reference for formatting (see comment at the bottom)
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT-0"));
        String formattedDate = sdf.format(date);
        return formattedDate;*/
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds),
                        TimeZone.getDefault().toZoneId());
    }


}
