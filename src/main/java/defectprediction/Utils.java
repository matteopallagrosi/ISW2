package defectprediction;

import static java.lang.System.*;

import org.json.JSONException;
import org.json.JSONObject;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

public class Utils {

    private Utils() {}

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
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds),
                        TimeZone.getDefault().toZoneId());
    }

    public static void convertCsvToArff(String fileName) throws IOException {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(fileName));
        Instances data = loader.getDataSet();

        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        String[] parts = fileName.split("\\.");
        saver.setFile(new File(parts[0] + ".arff"));
        saver.writeBatch();
        replaceLine(parts[0] + ".arff");
    }

    public static void replaceLine(String filePath) throws IOException {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        StringBuilder content = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            content = new StringBuilder();
            String line;

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (lineNumber == 13) {
                    content.append("@attribute Buggy {Yes,No}").append(System.lineSeparator());
                } else {
                    content.append(line).append(System.lineSeparator());
                }
                lineNumber++;
            }
        } finally {
            if (reader != null) reader.close();
        }
        try {
            writer = new BufferedWriter(new FileWriter(filePath));
            writer.write(content.toString());
            writer.close();

            out.println("Linea sostituita correttamente.");

        } catch (IOException e) {
            out.println("Si è verificato un errore durante la sostituzione della linea: " + e.getMessage());
        } finally {
            if (writer != null) writer.close();
        }
    }

}
