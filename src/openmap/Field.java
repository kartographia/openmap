package openmap;

public class Field {

    private String name;
    private Value value;

    public Field(String name, Object value){
        this.name = name;
        if (value instanceof Value){
            this.value = (Value) value;
        }
        else{
            this.value = new Value(value);
        }
    }

    public String getName(){
        return name;
    }

    public Value getValue(){
        return value;
    }

}