package com.nrims;

/**
 * A container class for storing properties needed to generate a Composite image.
 *
 * @author cpoczatek
 */
public class CompositeProps {

    //-----------------------------
    static final long serialVersionUID = 2L;
    //-----------------------------
    // DO NOT! Change variable order/type
    // DO NOT! Delete variables
    private MimsPlus[] images;
    //------------------------------
    //End of v2


   /** Default constructor. */
   public CompositeProps(){}

   /**
    * Instantiates a CompositeProps object with images <code>imgs</code>.
    * @param imgs set of images used to create the composite image.
    */
   public CompositeProps(MimsPlus[] imgs) {
       this.images = imgs;

   }

    /**
     * Two <code>CompositeProps</code> objects are equal if the <code>MimsPlus</code>
     * objects that make them up are equal.
     *
     * @param cp a <code>CompositeProps</code> object.
     * @return <code>true</code> if <code>this</code> and <code>cp</code> are equal.
     */
    public boolean equals(CompositeProps cp) {

       // If lengths are different then obviously they are not equal.
       MimsPlus[] cps = cp.getImages();
       if (cps.length != images.length)
          return false;

       // If the contents of the images array of the two objects differ
       // then the two objects are considered different, even if the
       // contents are the same, but in a different order.
       for (int i = 0; i < images.length; i++){
           //if neither is null, check if images are equal
           if (cps[i] != null && images[i] != null) {
               if (!cps[i].equals(images[i])) {
                   return false;
               }
           }
           //if one is null and the other not they are unequal
           if( (cps[i] != null && images[i] == null) || (cps[i] == null && images[i] != null))
            return false;
       }

       return true;
   }

   /**
    * Sets the images to be used for generating the composite image.
    * @param imgs set of images used to create the composite image.
    */
   public void setImages(MimsPlus[] imgs) { this.images = imgs ; }

   /**
    * Gets the images used to generate the composite image.
    * @return the array of images used to create the composite image.
    */
   public MimsPlus[] getImages() { return this.images ; }

}
