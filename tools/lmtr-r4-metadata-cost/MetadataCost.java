import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
import org.lmthermal.lmtr.LmtrJson;
import org.lmthermal.lmtr.JsonNumber;

/** Host-only parser/canonicalization attribution; no GC, camera or admission policy. */
public class MetadataCost {
  static long maps, entries, lists, elements, strings, utf16, numbers, coefficientDigits;
  /** Count structural owners after timing; no claim about JVM/ART object sizes. */
  static void count(Object value) {
    if(value instanceof Map<?,?> map) {
      maps++; entries+=map.size();
      for(var item:map.entrySet()) {count(item.getKey()); count(item.getValue());}
    } else if(value instanceof List<?> list) {
      lists++; elements+=list.size();for(Object item:list)count(item);
    } else if(value instanceof String text) {strings++;utf16+=text.length();}
    else if(value instanceof JsonNumber number) {numbers++;coefficientDigits+=number.getDecimal().precision();}
  }
  /** Attribute eight parse+canonical-encode samples after four warmups, without forced GC. */
  public static void main(String[] args) throws Exception {
    ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();
    for(String name:args) {
      byte[] input=Files.readAllBytes(Path.of(name));long[] cost=new long[8];long[] ns=new long[8];
      Map<String,?> parsed=null;
      for(int sample=-4;sample<8;sample++) {
        long bytes=bean.getThreadAllocatedBytes(id), start=System.nanoTime();
        parsed=LmtrJson.INSTANCE.parse(input);byte[] encoded=LmtrJson.INSTANCE.encode(parsed);
        long time=System.nanoTime()-start, allocated=bean.getThreadAllocatedBytes(id)-bytes;
        if(!Arrays.equals(input,encoded))throw new AssertionError("not canonical input");
        if(sample>=0){cost[sample]=allocated;ns[sample]=time;}
      }
      maps=entries=lists=elements=strings=utf16=numbers=coefficientDigits=0;count(parsed);
      Arrays.sort(cost);Arrays.sort(ns);
      System.out.printf(Locale.ROOT,"{\"case\":\"%s\",\"input_bytes\":%d,\"allocated_p50_bytes\":%d,\"allocated_max_bytes\":%d,\"wall_p50_ns\":%d,\"maps\":%d,\"properties\":%d,\"lists\":%d,\"elements\":%d,\"strings_including_keys\":%d,\"utf16_units\":%d,\"numbers\":%d,\"coefficient_digits\":%d}%n",
        Path.of(name).getFileName(),input.length,cost[4],cost[7],ns[4],maps,entries,lists,elements,strings,utf16,numbers,coefficientDigits);
    }
  }
}
