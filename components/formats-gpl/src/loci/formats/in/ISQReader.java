package loci.formats.in;


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

import loci.formats.tools.ImageInfo;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.units.UNITS;



public class IsqReader extends FormatReader {

    // -- Constants --
    private static final String ISQ_MAGIC_STRING = "CTDATA-HEADER_V1";
    private static boolean useMappedByteBuffer;
    private static final String ISQ_FILE = ""


    // -- Fields --
    private RandomAccessInputStream pixelFile;
    public int[] offsets;
    private int pixelOffset;

    // -- Constructor --
    /** Constructs a new Columbus reader. */
    public IsqReader() {
        super("ISQ", "isq");
        suffixSufficient = false;
        domains = new String[]{"Medical Imaging"};
        datasetDescription = "a single .isq file";
    }


    // -- IFormatReader API methods --



     /** @see loci.formats.IFormatReader#isThisType(String, boolean) */
 public boolean isThisType(String name, boolean open) {
     if (checkSuffix(name, "isq")) {
         return true;
     }
     else { return false;
     }
    }

    /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
    @Override
    public boolean isThisType(RandomAccessInputStream stream) throws IOException {
        final int blockLen = 512;
        if (!FormatTools.validStream(stream, blockLen, false)) {
            return false;
        } else {
            //double check this, not sure if correct
            stream.seek(508L);
            String lastFour = stream.readString(4);
            return lastFour.equals("isq");
        }

    }
    /** @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int) */
    //Obtains a sub-image of the specified image plane into a pre-allocated byte array.
    @Override
    public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h) throws FormatException, IOException {

        FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

        this.pixelFile.seek(0);
        int bpp = FormatTools.getBytesPerPixel(this.getPixelType());
        long planeSize = this.getSizeX() * this.getSizeY() * bpp;
        this.pixelFile.seek((long) this.pixelOffset + (long) no * planeSize);
        this.readPlane(this.pixelFile, x, y, w, h, buf);

        return buf;
    }

    /** @see loci.formats.IFormatReader#close(boolean) */
    public void close(boolean fileOnly) throws IOException {
        super.close(fileOnly);
        if (this.pixelFile != null) {
            this.pixelFile.close();
        }
    }
    // more cases to cover here?


    public void reopenFile() throws IOException {
        super.reopenFile();

        //this method necessary? 
    }

    // -- Internal FormatReader API methods --


    /** @see loci.formats.FormatReader#initFile(String) */
    @Override
    protected void initFile(String id) throws FormatException, IOException {
        super.initFile(id);
        this.in = new RandomAccessInputStream(id);

        if (id.endsWith(".isq")) {
            LOGGER.info("Looking for header file");
            String header = id.substring(0, id.lastIndexOf(".")) + ".isq";
            String magic = in.readString(16);
            setId(header);
            if (magic != ISQ_MAGIC_STRING) {
                throw new FormatException("Header file not found.");
            }

            LOGGER.info("Reading header");

            String imageName = in.readString(40); //name at char[40]


            in.seek(12);
            CoreMetadata m = core.get(0);

            //dimx_p is the dimension in pixels, dimx_um the dimension in microns.
            m.sizeX = in.readInt(); //dimx_p [12]
            m.sizeY = in.readInt(); //dimy_p [13]
            m.sizeZ = in.readInt(); //dimz_p [14] = nr of slices in current series

            float x_um = in.readFloat(); //dimx_um [15]
            float y_um = in.readFloat(); //dimy_um [16]
            float z_um = in.readFloat(); //dimz_um [17]


            String date = in.readString(2); //creation_date [2]

            LOGGER.info("Calculating image offsets");
            m.imageCount = getSizeZ();
            offsets = new int[getImageCount()];

            int offset = 512 + (512 * getSizeC());
            for (int i=0; i<getSizeC(); i++) {
                for (int j=0; j<getSizeZ(); j++) {
                    offsets[i*getSizeZ() + j] = offset + (j * getSizeX() * getSizeY());
                }
                offset += getSizeX() * getSizeY() * getSizeZ();
            }

            addGlobalMeta("Image name", imageName);
            addGlobalMeta("Creation date", date);

            LOGGER.info("Populating metadata");


            // The metadata store we're working with.
            MetadataStore store = this.makeFilterMetadata();
            MetadataTools.populatePixels(store, this);


            if (this.getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {

                // populate Dimensions data
                //Formats the input value for the physical size into a length in microns
                Length sizeX = FormatTools.getPhysicalSizeX((double) x_um);
                Length sizeY = FormatTools.getPhysicalSizeY((double) y_um);
                Length sizeZ = FormatTools.getPhysicalSizeZ((double) z_um);
                if (sizeX != null) {
                    store.setPixelsPhysicalSizeX(sizeX, 0);
                }

                if (sizeY != null) {
                    store.setPixelsPhysicalSizeY(sizeY, 0);
                }

                if (sizeZ != null) {
                    store.setPixelsPhysicalSizeZ(sizeZ, 0);
                }
                m.dimensionOrder = "XYZTC";
                m.rgb = false;


            }

        }

    }

}
