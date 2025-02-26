package openmap;
import static com.bbn.openmap.dataAccess.shape.ShapeConstants.*;
import com.bbn.openmap.dataAccess.shape.input.*;
import com.bbn.openmap.dataAccess.shape.output.*;

import org.locationtech.jts.geom.*;

import java.io.*;
import java.util.*;

//******************************************************************************
//**  ShapeFile
//******************************************************************************
/**
 *   Used to read and write shapefiles. Relies on OpenMap for encoding/decoding
 *   shapefiles and JTS for storing geometries
 *
 ******************************************************************************/

public class ShapeFile {

    private java.io.File shp;
    private java.io.File dbf;
    private java.io.File shx;
    private java.io.File prj;
    private String[] columns;
    private int numShapes;

    //private Iterator<ArrayList<Object>> attributes;
    //private Iterator<Geometry> geometries;
    private ArrayList<Record> records;


    private static PrecisionModel precisionModel = new PrecisionModel();
    private static GeometryFactory geometryFactory = new GeometryFactory(precisionModel, 4326);


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to create a new shapefile
   */
    public ShapeFile(){
        numShapes = 0;
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to open an existing shapefile
   */
    public ShapeFile(java.io.File file) throws Exception {
        if (!file.exists() || !file.isFile()) throw new IllegalArgumentException("Invalid file");

        String ext = getExtension(file).toLowerCase();
        if (ext.equals("dbf")){
            dbf = file;
            shp = getFile(file, "shp");
            shx = getFile(file, "shx");
        }
        else if (ext.equals("shp")){
            shp = file;
            dbf = getFile(file, "dbf");
            shx = getFile(file, "shx");
        }
        else if (ext.equals("shx")){
            shx = file;
            shp = getFile(file, "shp");
            dbf = getFile(file, "dbf");
        }

      //Parse index
        ShxInputStream shxInputStream = new ShxInputStream(new FileInputStream(shx));
        int[][] index = shxInputStream.getIndex();
        numShapes = index[1].length;
        shxInputStream.close();
    }


  //**************************************************************************
  //** addRecord
  //**************************************************************************
  /** Used to add a record to the shapefile
   */
    public void addRecord(Object... args) throws Exception {

      //Initialize records array
        updateRecords();


      //Create new record
        Record record;
        if (args[0] instanceof Record){
            record = (Record) args[0];
        }
        else{
            Field[] fields = new Field[args.length];
            for (int i=0; i<fields.length; i++){
                String name = null;
                Object value = args[i];
                fields[i] = new Field(name, value);
            }
            record = new Record(fields);
        }


      //Add record to the records array and increment number of shapes
        records.add(record);
        numShapes++;
    }


  //**************************************************************************
  //** getRecordCount
  //**************************************************************************
  /** Returns the total number of records in the shapefile.
   */
    public int getRecordCount(){
        return numShapes;
    }


  //**************************************************************************
  //** getRecords
  //**************************************************************************
  /** Used to iterate through the records in the shapefile. Records are parsed
   *  on demand. Nothing is stored in memory. Be sure to iterate through all
   *  the records so that the input streams close correctly.
   */
    public Iterator<Record> getRecords() throws Exception {


      //Parse dbf
        DbfInputStream dbfInputStream = new DbfInputStream(new FileInputStream(dbf));
        columns = dbfInputStream.getColumnNames();
        Iterator<ArrayList<Object>> attributes = dbfInputStream.getRecords();


      //Parse shp
        Iterator<Geometry> geometries = new ShapeIterator(new FileInputStream(shp));


      //Return iterator
        return new Iterator<Record>(){
            private int id = 1;
            public Record next(){
                if (!hasNext()) throw new NoSuchElementException();

                Geometry geom = geometries.next();
                ArrayList<Object> attr = attributes.next();
                Field[] fields = new Field[attr.size()+2];
                for (int i=0; i<attr.size(); i++){
                    Value value = new Value(attr.get(i));
                    String name = columns[i];
                    Field field = new Field(name, value);
                    fields[i+1] = field;
                }
                fields[0] = new Field("id*", id);
                fields[fields.length-1] = new Field("geom*", geom);


                if (geom!=null){
                    if (id-1!=(Integer) geom.getUserData()){
                        //Reparse dbf?
                        throw new RuntimeException("Shp/Dbf Record Mismatch");
                    }
                }
                id++;
                return new Record(fields);
            }
            public boolean hasNext(){
                return geometries.hasNext();
            }
        };
    }


  //**************************************************************************
  //** updateRecords
  //**************************************************************************
  /** Used to initialize the records array as needed. If an existing shapefile
   *  was used to instantiate this class, the array is populated with records
   *  from the shapefile. Note that this method is only used to create new
   *  shapefiles.
   */
    private void updateRecords() throws Exception {
        if (records==null){
            records = new ArrayList<>();
            if (shp!=null){
                Iterator<Record> it = getRecords();
                while (it.hasNext()){
                    records.add(it.next());
                    numShapes++;
                }
            }
        }
    }


  //**************************************************************************
  //** getExtents
  //**************************************************************************
  /** Returns lat/lon coordinates for the lower left and upper right
   */
    private double[] getExtents(){
        return new double[] { -90, -180, 90, 180 };
    }


  //**************************************************************************
  //** getGeometries
  //**************************************************************************
  /** Used to iterate through the geometries (aka shapes) in the shapefile. Be
   *  sure to iterate through all the records so that the input streams close
   *  correctly.
   */
    public Iterator<Geometry> getGeometries() throws Exception {
        return new ShapeIterator(new FileInputStream(shp));
    }


  //**************************************************************************
  //** delete
  //**************************************************************************
  /** Used to delete the shapefile
   */
    public void delete(){
        if (shx!=null) shx.delete();
        if (shp!=null) shp.delete();
        if (dbf!=null) dbf.delete();
        if (prj!=null) prj.delete();
    }



  //**************************************************************************
  //** saveAs
  //**************************************************************************
    public void saveAs(String name, java.io.File dir) throws Exception{

      //Initialize records array
        updateRecords();

      //Check if the records array is empty
        if (records.isEmpty()) throw new Exception("Nothing to save!");


      //Iterate through the records and find the first geometry field. This
      //will be used to itentify the geometry column and type.
        int geomIndex = -1;
        Integer shapeType = null;
        for (Record record : records){
            Field[] fields = record.getFields();
            for (int j=0; j<fields.length; j++){
                Field field = fields[j];
                Object value = field.getValue().toObject();
                if (value instanceof Point || value instanceof MultiPoint){
                    shapeType = SHAPE_TYPE_POINT;
                }
                else if (value instanceof LineString || value instanceof MultiLineString){
                    shapeType = SHAPE_TYPE_POLYLINE;
                }
                else if (value instanceof Polygon || value instanceof MultiPolygon){
                    shapeType = SHAPE_TYPE_POLYGON;
                }
                if (shapeType!=null){
                    geomIndex = j;
                    break;
                }
            }

            if (shapeType!=null) break;
        }
        if (shapeType==null) throw new Exception("Records missing geometry");


      //Create index
        int[][] index = new int[2][records.size()];
        int pos = 50;

        if (shapeType==SHAPE_TYPE_POINT){
            for (int i=0; i<records.size(); i++){
                int contentLength = 0;
                contentLength += 2; // Shape Type

                Object geom = records.get(i).get(geomIndex);
                if (geom instanceof MultiPoint){
                    MultiPoint mp = (MultiPoint) geom;
                    contentLength += (4 * 4); // bounding box, 4 doubles
                    contentLength += 2; // number of points, 1 int
                    contentLength += (mp.getNumGeometries() * (2 + 4)); // points, 2 doubles each
                }
                else{
                    contentLength += 4; // X
                    contentLength += 4; // Y
                }
                index[1][i] = contentLength;
                index[0][i] = pos;
                pos += contentLength + 4;
            }
        }
        else if (shapeType == SHAPE_TYPE_POLYLINE || shapeType == SHAPE_TYPE_POLYGON) {
            for (int i=0; i<records.size(); i++){
                int contentLength = 0;
                contentLength += 2; // Shape Type
                contentLength += 16; // Box
                contentLength += 2; // NumParts
                contentLength += 2; // NumPoints


                int numShapes;
                int numCoordinates = 0;
                Object geom = records.get(i).get(geomIndex);
                if (geom instanceof MultiLineString || geom instanceof MultiPolygon){

                    if (geom instanceof MultiPolygon){
                        MultiPolygon mp = (MultiPolygon) geom;
                        numShapes = mp.getNumGeometries();
                        for (int j=0; j<numShapes; j++){
                            numCoordinates += mp.getGeometryN(j).getCoordinates().length;
                        }
                    }
                    else{
                        MultiLineString mp = (MultiLineString) geom;
                        numShapes = mp.getNumGeometries();
                        for (int j=0; j<numShapes; j++){
                            numCoordinates += mp.getGeometryN(j).getCoordinates().length;
                        }
                    }

                }
                else{
                    numShapes = 1;
                    numCoordinates = ((Geometry) geom).getCoordinates().length;
                }

                contentLength += (numShapes * 2); // offsets?
                contentLength += (numCoordinates * (2 + 4)); // points, 2 doubles each

                index[1][i] = contentLength;
                index[0][i] = pos;
                pos += contentLength + 4;
            }
        }


        if (!dir.exists()) dir.mkdirs();


      //Save index (shx file)
        shx = new java.io.File(dir, name + ".shx");
        ShxOutputStream shxOutputStream = new ShxOutputStream(new FileOutputStream(shx));
        shxOutputStream.writeIndex(index, shapeType, getExtents());


      //Save geometries (shp)
        shp = new java.io.File(dir, name + ".shp");
        ShpOutputStream shpOutputStream = new ShpOutputStream(new FileOutputStream(shp));
        shpOutputStream.write(shapeType, index, geomIndex);


      //Save attributes
        dbf = new java.io.File(dir, name + ".dbf");
        DbfOutputStream dbfOutputStream = new DbfOutputStream(new FileOutputStream(shp));
        //dbfOutputStream.writeModel(model);
    }


  //**************************************************************************
  //** ShapeIterator
  //**************************************************************************
  /** Used to read a shapefile from an input stream
   */
    private class ShapeIterator implements Iterator<Geometry> {
        private LittleEndianInputStream _leis = null;

        private int shapeType;

        public ShapeIterator(InputStream is) throws Exception {


            BufferedInputStream bis = new BufferedInputStream(is);
            _leis = new LittleEndianInputStream(bis);


            /* int fileCode = */_leis.readInt();
            _leis.skipBytes(20);
            /* int fileLength = */_leis.readInt();
            /* int version = */_leis.readLEInt();
            shapeType = _leis.readLEInt();
            /* double xMin = */_leis.readLEDouble();
            /* double yMin = */_leis.readLEDouble();
            /* double xMax = */_leis.readLEDouble();
            /* double yMax = */_leis.readLEDouble();
            /* double zMin = */_leis.readLEDouble();
            /* double zMax = */_leis.readLEDouble();
            /* double mMin = */_leis.readLEDouble();
            /* double mMax = */_leis.readLEDouble();

            if (shapeType==SHAPE_TYPE_POINT){
                //getPoints();
            }
            else if (shapeType == SHAPE_TYPE_POLYLINE ||
                shapeType == SHAPE_TYPE_POLYGON) {
                //getPolys(shapeType);
            }
            else {
                throw new Exception("Unsupported shape: " + shapeType);
            }
        }

        private int i = 0;

        public boolean hasNext() {
            return i < numShapes;
        }

        public Geometry next() {
            if (!hasNext()) throw new NoSuchElementException();
            Geometry geom = null;
            try{
                if (shapeType==SHAPE_TYPE_POINT){
                    geom = getPoint();
                }
                else if (shapeType == SHAPE_TYPE_POLYLINE ||
                    shapeType == SHAPE_TYPE_POLYGON) {
                    geom = getPoly();
                }

                i++;

                if (!hasNext()) close();
            }
            catch(Exception e){
                try{close();}catch(Exception ex){}
                throw new RuntimeException(e);
            }

            return geom;
        }


        private Geometry getPoint() throws Exception {
            Geometry geom = null;

            int shpRecord = _leis.readInt();
            /* int shpContentLength = */_leis.readInt();
            int shpType = _leis.readLEInt();
            if (shpType != SHAPE_TYPE_NULL) {

                double lon = _leis.readLEDouble();
                double lat = _leis.readLEDouble();
                int idx = new Integer(shpRecord - 1);

                geom = geometryFactory.createPoint(new Coordinate(lon, lat));
                geom.setUserData(idx);
            }

            return geom;
        }


        private Geometry getPoly() throws Exception {
            Geometry geom = null;

            Integer idx = new Integer(_leis.readInt() - 1);
            /* int shpContentLength = */_leis.readInt();
            int shpType = _leis.readLEInt();

            if (shpType != SHAPE_TYPE_NULL) {

                /* double xLeft = */_leis.readLEDouble();
                /* double xBottom = */_leis.readLEDouble();
                /* double xRight = */_leis.readLEDouble();
                /* double xTop = */_leis.readLEDouble();
                int numParts = _leis.readLEInt();
                int numPoints = _leis.readLEInt();

                int[] offsets = new int[numParts];
                for (int n = 0; n < numParts; n++) {
                    offsets[n] = _leis.readLEInt();
                }


                ArrayList<Geometry> list = new ArrayList<Geometry>(numParts);
                for (int j=0; j<numParts; j++) {

                    int numVertices;
                    if (j != numParts - 1) {
                       numVertices = (offsets[j + 1]) - offsets[j];
                    }
                    else {
                       numVertices = (numPoints - offsets[j]);
                    }


                    Coordinate[] coordinates = new Coordinate[numVertices];
                    for (int n = 0; n < numVertices; n++) {
                       double lon = _leis.readLEDouble();
                       double lat = _leis.readLEDouble();
                       coordinates[n] = new Coordinate(lon, lat);
                    }


                    if (shapeType == SHAPE_TYPE_POLYLINE) {
                        list.add(geometryFactory.createLineString(coordinates));
                    }
                    else if (shapeType == SHAPE_TYPE_POLYGON) {
                       list.add(geometryFactory.createPolygon(coordinates));
                    }
                }


                if (numParts>1){
                    if (shapeType == SHAPE_TYPE_POLYLINE) {
                        LineString[] arr = new LineString[list.size()];
                        for (int j=0; j<arr.length; j++){
                            arr[j] = (LineString) list.get(j);
                        }
                        geom = geometryFactory.createMultiLineString(arr);
                    }
                    else if (shapeType == SHAPE_TYPE_POLYGON) {
                        Polygon[] arr = new Polygon[list.size()];
                        for (int j=0; j<arr.length; j++){
                            arr[j] = (Polygon) list.get(j);
                        }
                        geom = geometryFactory.createMultiPolygon(arr);
                    }
                }
                else{
                    geom = list.get(0);
                }
            }

            if (geom!=null) geom.setUserData(idx);
            return geom;
        }



        public void close() throws IOException {
           _leis.close();
        }
    }


  //**************************************************************************
  //** ShpOutputStream
  //**************************************************************************
  /** Used to write a shapefile to an output stream
   */
    private class ShpOutputStream {
        private LittleEndianOutputStream _leos = null;
        public final static int ESRI_RECORD_HEADER_LENGTH = 4; // length in 16-bit words

        public ShpOutputStream(OutputStream os) {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            _leos = new LittleEndianOutputStream(bos);
        }

        public void write(int shapeType, int[][] indexData, int geomIndex) throws Exception {

            _leos.writeInt(9994); // Byte 0 File Code
            _leos.writeInt(0); // Byte 4 Unused
            _leos.writeInt(0); // Byte 8 Unused
            _leos.writeInt(0); // Byte 12 Unused
            _leos.writeInt(0); // Byte 16 Unused
            _leos.writeInt(0); // Byte 20 Unused


            int contentLength = 50;

            if (!records.isEmpty()) {
               contentLength = indexData[0][indexData[0].length - 1] + indexData[1][indexData[0].length - 1] + ESRI_RECORD_HEADER_LENGTH;
            }

            _leos.writeInt(contentLength); // Byte 24 File Length
            _leos.writeLEInt(1000); // Byte 28 Version
            _leos.writeLEInt(shapeType); // Byte 32 Shape Type

            // Writes bounding box.
            double[] extents = getExtents();
            writeExtents(extents);

            _leos.writeDouble(0.0); // Byte 68
            _leos.writeDouble(0.0); // Byte 76
            _leos.writeDouble(0.0); // Byte 84
            _leos.writeDouble(0.0); // Byte 92

            for (int i=0; i<records.size(); i++){
                // Record header...
                _leos.writeInt(i + 1); // Record numbers start with 1
                _leos.writeInt(indexData[1][i]);

                // Beginning of Geometry data
                _leos.writeLEInt(shapeType);


                Object geom = records.get(i).get(geomIndex);
                if (shapeType==SHAPE_TYPE_POINT){
                    writePoints(geom);
                }
                else if (shapeType == SHAPE_TYPE_POLYLINE ||
                    shapeType == SHAPE_TYPE_POLYGON) {
                    writePolys(geom);
                }


            }

            _leos.flush();
            _leos.close();
        }

        private void writePoints(Object geom) throws Exception{
            if (geom instanceof MultiPoint){
                MultiPoint mp = (MultiPoint) geom;

                // Writes bounding box.
                writeExtents(mp.getEnvelopeInternal());


                // Write number of points per shape
                int numShapes = mp.getNumGeometries();
                _leos.writeLEInt(numShapes);

                // Write the geometry for each part
                for (int i = 0; i < numShapes; i++) {
                    writeCoordinates(mp.getGeometryN(i).getCoordinates());
                }
            }
            else{
                Point point = (Point) geom;
                writeCoordinates(point.getCoordinates());
            }
        }




        private void writePolys(Object geom) throws Exception{


            Envelope extents;
            int numShapes;
            int numPoints = 0;
            ArrayList<Coordinate[]> shapes = new ArrayList<Coordinate[]>();
            if (geom instanceof MultiPolygon){
                MultiPolygon mp = (MultiPolygon) geom;
                extents = mp.getEnvelopeInternal();
                numShapes = mp.getNumGeometries();
                for (int i=0; i<numShapes; i++) {
                    Coordinate[] coordinates = mp.getGeometryN(i).getCoordinates();
                    numPoints += coordinates.length;
                    shapes.add(coordinates);
                }
            }
            else if (geom instanceof MultiLineString){
                MultiLineString mp = (MultiLineString) geom;
                extents = mp.getEnvelopeInternal();
                numShapes = mp.getNumGeometries();
                for (int i=0; i<numShapes; i++) {
                    Coordinate[] coordinates = mp.getGeometryN(i).getCoordinates();
                    numPoints += coordinates.length;
                    shapes.add(coordinates);
                }
            }
            else{ //Simple LineString or Polygon
                Geometry g = (Geometry) geom;
                extents = g.getEnvelopeInternal();
                Coordinate[] coordinates = g.getCoordinates();
                numPoints = coordinates.length;
                numShapes = 1;
                shapes.add(coordinates);
            }

            // Write extents
            writeExtents(extents);


            // Writes number of parts
            _leos.writeLEInt(numShapes);


            // Write total number of points
            _leos.writeLEInt(numPoints);


            // Write the offsets to each part for a given shape
            int pos = 0;
            for (Coordinate[] coordinates : shapes) {
                _leos.writeLEInt(pos);
                pos += coordinates.length*2;
            }


            for (Coordinate[] coordinates : shapes) {
                writeCoordinates(coordinates);
            }

        }




//      int pos = 0;
//      int[] offsets = new int[sublist.size()];
//      for (int j = 0; j < sublist.size(); j++) {
//         OMPoly poly = (OMPoly) sublist.getOMGraphicAt(j);
//         double[] data = poly.getLatLonArray();
//         offsets[j] = pos / 2;
//         pos += data.length;
//      }



//        public void close() throws IOException {
//            _leos.close();
//        }

        private void writeCoordinates(Coordinate[] coordinates) throws IOException {
            for (Coordinate coordinate : coordinates){
                writeCoordinate(coordinate);
            }
        }

        private void writeCoordinate(Coordinate coordinate) throws IOException {
            double lon = coordinate.x;
            double lat = coordinate.y;
            _leos.writeLEDouble(lon);
            _leos.writeLEDouble(lat);
        }

        private void writeExtents(Envelope envelope) throws IOException {
            _leos.writeLEDouble(envelope.getMinX()); //-180.0
            _leos.writeLEDouble(envelope.getMinY()); //-90.0
            _leos.writeLEDouble(envelope.getMaxX()); //180.0
            _leos.writeLEDouble(envelope.getMaxY()); //90.0
        }

        private void writeExtents(double[] extents) throws IOException {
            //return new double[] { -90, -180, 90, 180 };

            _leos.writeLEDouble(extents[1]); //-180.0
            _leos.writeLEDouble(extents[0]); //-90.0
            _leos.writeLEDouble(extents[3]); //180.0
            _leos.writeLEDouble(extents[2]); //90.0
        }
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
    private java.io.File getFile(java.io.File file, String ext){
        java.io.File dir = file.getParentFile();
        String name = file.getName();
        int idx = name.lastIndexOf(".")+1;
        name = name.substring(0, idx) + ext;
        return new java.io.File(dir, name);
    }


  //**************************************************************************
  //** getExtension
  //**************************************************************************
  /** Returns the file's extension, excluding the last dot/period
   *  (e.g. "C:\image.jpg" will return "jpg"). Returns a zero-length string
   *  if there is no extension.
   */
    private String getExtension(java.io.File file){
        String name = file.getName();
        int idx = name.lastIndexOf(".");
        if (idx > -1) return name.substring(idx+1);
        else return "";
    }
}