// Part.java
// Clase que representa una refacción con id, nombre, cantidad y precio.
public class Part {
     private String id;
     private String name;
     private int quantity;
     private double price;
 
     public Part(String id, String name, int quantity, double price) {
         this.id = id;
         this.name = name;
         this.quantity = quantity;
         this.price = price;
     }
 
     public String getId() { return id; }
     public String getName() { return name; }
     public int getQuantity() { return quantity; }
     public void setQuantity(int quantity) { this.quantity = quantity; }
     public double getPrice() { return price; }
 
     @Override
     public String toString() {
          return "Part [id=" + id + ", name =" + name + ", quantity=" + quantity + ", price=" + price + "]";
     }
 }
 