package edu.illinois.library.cantaloupe.operation;

import static org.junit.Assert.*;

import edu.illinois.library.cantaloupe.image.Compression;
import edu.illinois.library.cantaloupe.image.Format;
import edu.illinois.library.cantaloupe.image.Identifier;
import edu.illinois.library.cantaloupe.test.BaseTest;
import edu.illinois.library.cantaloupe.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import java.awt.Dimension;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class OperationListTest extends BaseTest {

    private OperationList ops;

    private static OperationList newOperationList() {
        OperationList ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.jpg"));
        Crop crop = new Crop();
        crop.setFull(true);
        ops.add(crop);
        Scale scale = new Scale();
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.JPG);
        ops.setOutputCompression(Compression.JPEG);
        ops.setOutputQuality(80);
        ops.setOutputInterlacing(true);
        return ops;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        ops = newOperationList();
        assertNotNull(ops.getOptions());
    }

    /* add(Operation) */

    @Test
    public void testAdd() {
        ops = new OperationList();
        assertFalse(ops.iterator().hasNext());
        ops.add(new Rotate());
        assertTrue(ops.iterator().hasNext());
    }

    @Test
    public void testAddWhileFrozen() {
        ops = new OperationList();
        ops.freeze();
        try {
            ops.add(new Rotate());
            fail("Expected exception");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    /* clear() */

    @Test
    public void testClear() {
        int opCount = 0;
        Iterator it = ops.iterator();
        while (it.hasNext()) {
            it.next();
            opCount++;
        }
        assertEquals(3, opCount);
        ops.clear();

        opCount = 0;
        it = ops.iterator();
        while (it.hasNext()) {
            it.next();
            opCount++;
        }
        assertEquals(0, opCount);
    }

    @Test
    public void testClearWhileFrozen() {
        ops.freeze();
        try {
            ops.clear();
            fail("Expected exception");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    /* compareTo(OperationList) */

    @Test
    public void testCompareTo() {
        OperationList ops2 = new OperationList();
        ops2.setIdentifier(new Identifier("identifier.jpg"));
        Crop crop = new Crop();
        crop.setFull(true);
        ops2.add(crop);
        Scale scale = new Scale();
        ops2.add(scale);
        ops2.add(new Rotate(0));
        ops2.setOutputFormat(Format.JPG);
        ops2.setOutputCompression(Compression.JPEG);
        ops2.setOutputQuality(80);
        ops2.setOutputInterlacing(true);
        assertEquals(0, ops2.compareTo(this.ops));
    }

    /* equals(Object) */

    @Test
    public void testEqualsWithEqualOperationList() {
        OperationList ops1 = TestUtil.newOperationList();
        OperationList ops2 = TestUtil.newOperationList();
        ops2.add(new Rotate());
        assertTrue(ops1.equals(ops2));
    }

    @Test
    public void testEqualsWithUnequalOperationList() {
        OperationList ops1 = TestUtil.newOperationList();
        OperationList ops2 = TestUtil.newOperationList();
        ops2.add(new Rotate(1));
        assertFalse(ops1.equals(ops2));
    }

    /* getFirst(Class<Operation>) */

    @Test
    public void testGetFirst() {
        assertNull(ops.getFirst(MetadataCopy.class));
        assertNotNull(ops.getFirst(Scale.class));
    }

    /* getOptions() */

    @Test
    public void testGetOptions() {
        assertNotNull(ops.getOptions());
    }

    @Test
    public void testGetOptionsWhileFrozen() {
        ops.freeze();
        try {
            ops.getOptions().put("test", "test");
            fail("Expected exception");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    /* getResultingSize(Dimension) */

    @Test
    public void testGetResultingSize() {
        Dimension fullSize = new Dimension(300, 200);
        ops = new OperationList();
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        Rotate rotate = new Rotate();
        ops.add(crop);
        ops.add(scale);
        ops.add(rotate);
        assertEquals(fullSize, ops.getResultingSize(fullSize));

        ops = new OperationList();
        crop = new Crop();
        crop.setUnit(Crop.Unit.PERCENT);
        crop.setWidth(0.5f);
        crop.setHeight(0.5f);
        scale = new Scale(0.5f);
        ops.add(crop);
        ops.add(scale);
        assertEquals(new Dimension(75, 50), ops.getResultingSize(fullSize));
    }

    /* isNoOp() */

    @Test
    public void testIsNoOp1() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        Rotate rotate = new Rotate(0);
        Format format = Format.JPG;
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier"));
        ops.add(crop);
        ops.add(scale);
        ops.add(rotate);
        ops.setOutputFormat(format);
        assertFalse(ops.isNoOp());
    }

    @Test
    public void testIsNoOp2() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.JPG);
        assertFalse(ops.isNoOp()); // false because the identifier has no discernible source format
    }

    @Test
    public void testIsNoOp3() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.GIF);
        assertTrue(ops.isNoOp());
    }

    @Test
    public void testIsNoOp4() {
        Crop crop = new Crop();
        crop.setFull(false);
        crop.setX(30f);
        crop.setY(30f);
        crop.setWidth(30f);
        crop.setHeight(30f);
        Scale scale = new Scale();
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.GIF);
        assertFalse(ops.isNoOp());
    }

    @Test
    public void testIsNoOp5() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        scale.setMode(Scale.Mode.ASPECT_FIT_INSIDE);
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.GIF);
        assertTrue(ops.isNoOp());
    }

    @Test
    public void testIsNoOp6() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale(0.5f);
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.GIF);
        assertFalse(ops.isNoOp());
    }

    @Test
    public void testIsNoOp7() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(2));
        ops.setOutputFormat(Format.GIF);
        assertFalse(ops.isNoOp());
    }

    @Test
    public void testIsNoOp8() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);;
        ops.add(new Rotate());
        ops.setOutputFormat(Format.GIF);
        assertTrue(ops.isNoOp());
    }

    @Test
    public void testIsNoOp9() {
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.GIF);
        assertTrue(ops.isNoOp());
    }

    @Test
    public void testIsNoOp10() {
        // Set up a no-op....
        Crop crop = new Crop();
        crop.setFull(true);
        Scale scale = new Scale();
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.add(crop);
        ops.add(scale);
        ops.add(new Rotate(0));
        ops.setOutputFormat(Format.GIF);

        // Add a MetadataCopy
        ops.add(new MetadataCopy());

        assertTrue(ops.isNoOp());
    }

    /* isNoOp(Format) */

    @Test
    public void testIsNoOpWithSourceFormat() {
        // same format
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.gif"));
        ops.setOutputFormat(Format.GIF);
        assertTrue(ops.isNoOp(Format.GIF));

        // different formats
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.jpg"));
        ops.setOutputFormat(Format.GIF);
        assertFalse(ops.isNoOp(Format.JPG));
    }

    @Test
    public void testIsNoOpWithPdfSourceAndPdfOutputAndOverlay() {
        // same format
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.pdf"));
        ops.setOutputFormat(Format.PDF);
        assertTrue(ops.isNoOp(Format.PDF));
    }

    /* iterator() */

    @Test
    public void testIterator() {
        int count = 0;
        Iterator it = ops.iterator();
        while (it.hasNext()) {
            it.next();
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    public void testIteratorCannotRemoveWhileFrozen() {
        ops.freeze();
        Iterator it = ops.iterator();
        it.next();
        try {
            it.remove();
            fail("Expected exception");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    /* setIdentifier() */

    @Test
    public void testSetIdentifierWhileFrozen() {
        ops.freeze();
        try {
            ops.setIdentifier(new Identifier("alpaca"));
            fail("Expected exception");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    /* setOutputFormat() */

    @Test
    public void testSetOutputFormatWhileFrozen() {
        ops.freeze();
        try {
            ops.setOutputFormat(Format.GIF);
            fail("Expected exception");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    /* toFilename() */

    @Test
    public void testToFilename() {
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.jpg"));
        Crop crop = new Crop();
        crop.setX(5f);
        crop.setY(6f);
        crop.setWidth(20f);
        crop.setHeight(22f);
        ops.add(crop);
        Scale scale = new Scale(0.4f);
        ops.add(scale);
        ops.add(new Rotate(15));
        ops.add(ColorTransform.BITONAL);
        ops.setOutputFormat(Format.JPG);
        ops.getOptions().put("animal", "cat");

        String expected = "50c63748527e634134449ae20b199cc0_f694166f0f0aa4f0a88d5d7a7315a15f.jpg";
        assertEquals(expected, ops.toFilename());

        // Assert that changing an operation changes the filename
        crop.setX(10f);
        assertNotEquals(expected, ops.toFilename());

        // Assert that changing an option changes the filename
        crop.setX(5f);
        assertEquals(expected, ops.toFilename());
        ops.getOptions().put("animal", "dog");
        assertNotEquals(expected, ops.toFilename());
    }

    /* toMap() */

    @Test
    public void testToMap() {
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.jpg"));
        // crop
        Crop crop = new Crop();
        crop.setX(2);
        crop.setY(4);
        crop.setWidth(50);
        crop.setHeight(50);
        ops.add(crop);
        // no-op scale
        Scale scale = new Scale();
        ops.add(scale);
        ops.add(new Rotate(0));
        // transpose
        ops.add(Transpose.HORIZONTAL);
        // output
        ops.setOutputFormat(Format.JPG);
        ops.setOutputCompression(Compression.JPEG);
        ops.setOutputInterlacing(true);
        ops.setOutputQuality(80);

        final Dimension fullSize = new Dimension(100, 100);
        Map<String,Object> map = ops.toMap(fullSize);
        assertEquals("identifier.jpg", map.get("identifier"));
        assertEquals(2, ((List) map.get("operations")).size());
        assertEquals(0, ((Map) map.get("options")).size());
        assertEquals("jpg", ((Map) map.get("output_format")).get("extension"));
        assertEquals(80, map.get("output_quality"));
        assertTrue((boolean) map.get("output_interlacing"));
        assertEquals("JPEG", map.get("output_compression"));
    }

    /* toString() */

    @Test
    public void testToString() {
        ops = new OperationList();
        ops.setIdentifier(new Identifier("identifier.jpg"));
        Crop crop = new Crop();
        crop.setX(5f);
        crop.setY(6f);
        crop.setWidth(20f);
        crop.setHeight(22f);
        ops.add(crop);
        Scale scale = new Scale(0.4f);
        ops.add(scale);
        ops.add(new Rotate(15));
        ops.add(ColorTransform.BITONAL);
        ops.setOutputFormat(Format.JPG);
        ops.setOutputInterlacing(true);
        ops.setOutputQuality(80);
        ops.setOutputCompression(Compression.JPEG);
        ops.getOptions().put("animal", "cat");

        String expected = "identifier.jpg_crop:5,6,20,22_scale:40%_rotate:15_null_colortransform:bitonal_animal:cat_interlace_quality:80_compression:JPEG.jpg";
        assertEquals(expected, ops.toString());
    }


    /* validate() */

    @Test
    public void testValidateWithValidInstance() {
        Dimension fullSize = new Dimension(1000, 1000);
        OperationList ops = new OperationList();
        Crop crop = new Crop(0, 0, 100, 100);
        ops.add(crop);
        try {
            ops.validate(fullSize);
        } catch (ValidationException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testValidateWithOutOfBoundsCrop() {
        Dimension fullSize = new Dimension(1000, 1000);
        OperationList ops = new OperationList();
        Crop crop = new Crop(1001, 1001, 100, 100);
        ops.add(crop);
        try {
            ops.validate(fullSize);
            fail("Expected exception");
        } catch (ValidationException e) {
            // pass
        }
    }

}
