package xml_service;

public class AmbiguousMatch extends Exception {
   public AmbiguousMatch(String msg) {
      super(msg);
   }
   public AmbiguousMatch(Object obj) {
      super();
      Obj = obj;
   }
   public AmbiguousMatch() {
      super();
   }

   private Object Obj = null;
   public Object getObj() {
      return Obj;
   }
}

