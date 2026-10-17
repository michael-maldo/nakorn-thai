package au.com.nakornthai.staffapp.printing.data;

import android.content.Context;
import android.content.SharedPreferences;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import com.google.gson.*;
import java.util.*;

public final class PrinterConfigurationRepository {
  public interface Store {
    String read();

    boolean write(String json);
  }

  private final Store store;
  public String warning = "";

  public PrinterConfigurationRepository(Context c) {
    SharedPreferences p = c.getSharedPreferences("printer_profiles_v1", Context.MODE_PRIVATE);
    store =
        new Store() {
          public String read() {
            return p.getString("profiles", "{\"schemaVersion\":1,\"profiles\":[]}");
          }

          public boolean write(String s) {
            return p.edit().putString("profiles", s).commit();
          }
        };
  }

  public PrinterConfigurationRepository(Store s) {
    store = s;
  }

  public synchronized List<PrinterProfile> load() {
    warning = "";
    List<PrinterProfile> result = new ArrayList<>();
    try {
      JsonObject o = JsonParser.parseString(store.read()).getAsJsonObject();
      if (o.get("schemaVersion").getAsInt() != 1) throw new Exception();
      Set<String> ids = new HashSet<>();
      for (JsonElement e : o.getAsJsonArray("profiles")) {
        try {
          for (String field :
              new String[] {
                "id",
                "schemaVersion",
                "name",
                "type",
                "endpoint",
                "driver",
                "assignment",
                "timezone"
              })
            if (!e.getAsJsonObject().has(field) || e.getAsJsonObject().get(field).isJsonNull())
              throw new IllegalArgumentException("Missing profile field");
          PrinterProfile p = new Gson().fromJson(e, PrinterProfile.class);
          p.validate();
          if (!ids.add(p.id)) throw new Exception();
          result.add(p);
        } catch (Exception bad) {
          warning = "Invalid profiles skipped. Reconfigure affected devices.";
        }
      }
    } catch (Exception bad) {
      warning =
          "Printer configuration is malformed or uses an unsupported schema. Existing data"
              + " retained; saving is blocked.";
    }
    return result;
  }

  public synchronized void save(List<PrinterProfile> profiles) {
    load();
    if (warning.contains("saving is blocked")) throw new IllegalStateException(warning);
    Set<String> ids = new HashSet<>();
    for (PrinterProfile p : profiles) {
      p.validate();
      if (!ids.add(p.id)) throw new IllegalArgumentException("Duplicate profile identifier");
    }
    JsonObject o = new JsonObject();
    o.addProperty("schemaVersion", 1);
    o.add("profiles", new Gson().toJsonTree(profiles));
    if (!store.write(o.toString()))
      throw new IllegalStateException("Could not save printer settings");
  }
}
