# OpenMap
Java library used read/write shapefiles. Most of the code comes courtesy of [OpenMap](https://github.com/OpenMap-java/openmap).
All we have done here is to strip away all the rendering and UI code and provide a simple library to read and write shapefiles.


# Usage
The primary interface to the shapefile reader/writer is the ShapeFile class.
Here's an example of how to invoke the class. You can pass in any one of the shapefile files (shp, dbf, shx).

``` java
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
```


# Dependencies
This library relies on JTS. Specifically, jts-1.13.jar which is the last standalone releases of JTS.


# Java Compatibility
This library has been tested and used with Java 1.8 although it should work with Java 1.6 and up.


# License
The original OpenMap project has a very permissive [license](https://github.com/OpenMap-java/openmap/blob/master/LICENSE).
All that they ask is to get credit for their hard work and make any changes to the source code available to the community.

As such, this library is released under a very permissive MIT License. See the LICENSE.txt file for specifics.
Feel free to use the code and information found here as you like. This software comes with no guarantees or warranties.
You may use this software in any open source or commercial project provided that you keep in the spirit of the original
OpenMap license. The copyright headers in the original OpenMap classes have been retained as a courtesy to the original
authors and for your future reference.