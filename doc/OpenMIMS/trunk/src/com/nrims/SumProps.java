package com.nrims;

/**
 * The SumProps class is required to generate a sum
 * image and it also serves to store values related to
 * how the image is displayed. This class can be written to
 * disk as an object and later loaded by the OpenMIMS
 * plugin to automatically regenerate the image as
 * it was displayed at the time of saving.
 *
 * @author zkaufman
 */
public class SumProps implements java.io.Serializable {
      
    //-----------------------------
    static final long serialVersionUID = 2L;
    //-----------------------------
    // DO NOT! Change variable order/type
    // DO NOT! Delete variables
    static final int MASS_IMAGE = 0;
    static final int RATIO_IMAGE = 1;
      
   private int parentMassIdx, numMassIdx, denMassIdx;
   private double parentMassValue, numMassValue, denMassValue;
   private int sumType;
   private int xloc, yloc;
   private String dataFileName;
   private double ratioScaleFactor;
   //--------------------------------
   //End of v2
   private double mag = 1.0;
   private double minLUT;
   private double maxLUT;


    /**
     * Instatiates a SumProps object to create a sum image of a mass image.
     * @param massIndex the index of the mass image for which a sum image is being generated.
     */
    public SumProps(int massIndex) {
       this.parentMassIdx = massIndex;
       this.sumType = MASS_IMAGE;

       // Default values.
       xloc = -1;
       yloc = -1;
       numMassValue = -1.0;
       denMassValue = -1.0;
       ratioScaleFactor = -1.0;
    }        
    
    /**
     * Instatiates a SumProps object to create a sum image of a ratio image.
     * @param numIndex the index of the mass images of the numerator.
     * @param denIndex the index of the mass images of the denominator.
     */
    public SumProps(int numIndex, int denIndex) {
       this.numMassIdx = numIndex;
       this.denMassIdx = denIndex;
       this.sumType = RATIO_IMAGE;

       // Default values.
       xloc = -1;
       yloc = -1;
       numMassValue = -1.0;
       denMassValue = -1.0;
       ratioScaleFactor = -1.0;

       minLUT = 0.0;
       maxLUT = 1.0;
    }

    /**
     * Two <code>SumProps</code> objects are equal if numerator and denominator
     * are the same, in the case of a sum of a ratio image. Or if the parent mass
     * index is the same, in the case of a sum of a mass image.
     *
     * @param sp a <code>SumProps</code> object.
     * @return <code>true</code> if <code>this</code> and <code>sp</code> are equal.
     */
    public boolean equals(SumProps sp) {

       if (sumType != sp.getSumType())
          return false;

       if (sumType == RATIO_IMAGE) {
         if (sp.getNumMassIdx() == numMassIdx && sp.getDenMassIdx() == denMassIdx)
            return true;
      } else if (sumType == MASS_IMAGE) {
         if (sp.getParentMassIdx() == parentMassIdx)
            return true;
      }

       return false;
   }
          
   /**
    * Overwrites the index of the numerator mass set in the constructor.
    * @param numerator mass index (e.g. 0,1,2).
    */
   public void setNumMassIdx(int numerator) { this.numMassIdx = numerator; }

   /**
    * Gets the index of the mass image of the numerator.
    * @return int
    */
   public int getNumMassIdx() { return numMassIdx ; }

    /**
     * Sets the mass of the numerator image.
     * @param d mass of numerator image
     */
    public void setNumMassValue(double d) { numMassValue = d; }

    /**
     * Gets the mass of the numerator image.
     * @return mass of numerator image.
     */
    public double getNumMassValue() { return numMassValue; }

   /**
    * Overwrites the index of the denominator mass set in the constructor.
    * @param numerator mass index (e.g. 0,1,2).
    */
   public void setDenMassIdx(int denomator) { this.denMassIdx = denomator; }

    /**
     * Gets the index of the denominator image.
     * @return index of the denominator image.
     */
    public int getDenMassIdx() { return denMassIdx; }

    /**
     * Sets the mass value of the denominator image.
     * @param d mass of denominator image.
     */
    public void setDenMassValue(double d) { denMassValue = d; }

    /**
     * Gets the mass value of the denominator image.
     * @return mass of denominator image.
     */
    public double getDenMassValue() { return denMassValue; }

    /**
     * Sets the scale factor used to create the image.
     * @param rsf scale factor (default 10,000).
     */
    public void setRatioScaleFactor(double rsf) { ratioScaleFactor = rsf; }

    /**
     * Gets the scale factor used to create the image.
     * @return the scale factor.
     */
    public double getRatioScaleFactor() { return ratioScaleFactor; }

    /**
    * Overwrites the index of the parent mass set in the constructor.
    * @param numerator mass index (e.g. 0,1,2).
     */
    public void setParentMassIdx(int parentMassIdx) { this.parentMassIdx = parentMassIdx; }

    /**
     * Gets the index of the parent mass.
     * @return the index of the parent image.
     */
    public int getParentMassIdx() { return parentMassIdx; }

    /**
     * Sets the mass value of the parent mass image.
     * @param d mass of parent image.
     */
    public void setParentMassValue(double d) { parentMassValue = d; }

    /**
     * Gets the mass of the parent mass image.
     * @return mass of parent image.
     */
    public double getParentMassValue() { return parentMassValue; }

    /**
     * Sets the x-value of the window location.
     * @param x x-value of the window location.
     */
    public void setXWindowLocation(int x) { this.xloc = x; }

    /**
     * Gets the x-value of the window location.
     * @return x-value of window location.
     */
    public int getXWindowLocation() { return this.xloc; }

    /**
     * Sets the y-value of the window location.
     * @param y y-value of the window location.
     */
    public void setYWindowLocation(int y) { this.yloc = y; }

    /**
     * Gets the y-value of the window location.
     * @return y-value of window location.
     */
    public int getYWindowLocation() { return this.yloc; }

    /**
     * Sets the name of the data file from which this image was derived.
     * @param fileName name of file (name only, do not include directory).
     */
    public void setDataFileName(String fileName) { dataFileName = fileName;}

    /**
     * Gets the name of the data file form which this image was derived.
     * @return name of file (name only, does not include a directory).
     */
    public String getDataFileName() { return dataFileName; }

    /**
     * Gets the type of sum image (Mass or Ratio).
     * @return MASS_IMAGE (0) if mass image, RATIO_IMAGE (1) if ratio image.
     */
    // Type of sum image (either Mass or Ratio).
    public int getSumType() { return sumType; }

    /**
     * Sets the magnification factor.
     * @param m magnification factor.
     */
    public void setMag(double m) { this.mag = m; }

    /**
     * Gets the magnification factor.
     * @return magnification factor.
     */
    public double getMag() { return this.mag; }

    /**
     * Sets the minimum value of the lut, for display purposes only.
     * @param min lut minimum value.
     */
    public void setMinLUT(double min) { this.minLUT = min; }

    /**
     * Gets the minimum value of the lut, for display purposes only.
     * @return minimum lut value.
     */
    public double getMinLUT() { return this.minLUT; }

    /**
     * Sets the maximum value of the lut, for diaply purposes only.
     * @param max lut maximum value.
     */
    public void setMaxLUT(double max) { this.maxLUT = max; }

    /**
     * Gets the maximum value of the lut, for display purposes only.
     * @return maximum lut value.
     */
    public double getMaxLUT() { return this.maxLUT; }
}
