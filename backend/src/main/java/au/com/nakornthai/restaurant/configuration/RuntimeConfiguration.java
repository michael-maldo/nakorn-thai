package au.com.nakornthai.restaurant.configuration;
import java.util.*;
/** Effective configuration port. Values never cross an HTTP response boundary. */
public interface RuntimeConfiguration {
 Snapshot snapshot();
 record Snapshot(Map<String,String> values) {
  public Snapshot {values=Map.copyOf(values);}
  public String text(String key){return values.getOrDefault(key,"");}
  public boolean flag(String key){return Boolean.parseBoolean(text(key));}
  public boolean configured(String category){return ConfigurationFields.configured(category,this);}
  @Override public String toString(){return "RuntimeConfiguration[redacted]";}
 }
}
