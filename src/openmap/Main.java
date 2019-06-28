package openmap;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {

        ShapeFile shp = new ShapeFile(new java.io.File(args[0]));
        System.out.println("Found " + shp.getRecordCount() + " records in the shapefile");

        Iterator<Record> it = shp.getRecords();
        while (it.hasNext()){
            Record record = it.next();
            for (Field field : record.getFields()){
                System.out.print(field.getValue() + "\t");
            }
            System.out.println();
            if (true) break;
        }
    }
}