package openmap;
import com.vividsolutions.jts.geom.*;

public class Record {

    private Field[] fields;

    protected Record(Field[] fields){
        this.fields = fields;
    }

    public Field[] getFields(){
        return fields;
    }

    protected Field get(int index){
        return fields[index];
    }

    public Value getValue(int index){
        return fields[index].getValue();
    }

    public Value getValue(String name){
        for (Field field : fields){
            if (field.getName().equalsIgnoreCase(name)){
                return field.getValue();
            }
        }
        for (Field field : fields){
            if (field.getName().equalsIgnoreCase(name + "*")){
                return field.getValue();
            }
        }
        return new Value(null);
    }


    protected void add(Geometry geom){
        Field[] arr = new Field[fields.length+1];
        for (int i=0; i<fields.length; i++){
            arr[i] = fields[i];
        }
        arr[fields.length] = new Field("geom", new Value(geom));
        fields = arr;
    }
}