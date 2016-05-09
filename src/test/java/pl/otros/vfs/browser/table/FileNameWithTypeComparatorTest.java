package pl.otros.vfs.browser.table;

import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by dsimon on 04.05.16.
 */
public class FileNameWithTypeComparatorTest {

    private String[] test1 = {"sftp://dsimon@vpapp01.fs.gk-software.com/var/log/valuephone/vpServerLoyalty.log.120.gz","sftp://dsimon@vpapp01.fs.gk-software.com/var/log/valuephone/vpServerMobile.log.6.gz"};
    private String[] test2 = {"sftp://dsimon@vpapp01.fs.gk-software.com/var/log/valuephone/vpServerLoyalty.log.53.gz","sftp://dsimon@vpapp01.fs.gk-software.com/var/log/valuephone/vpServerMobile.log.6.gz"};

    FileNameWithTypeComparator tested;

    @Before
    public void init(){
        tested = new FileNameWithTypeComparator();

    }

    @Test
    public void testComparator1(){
        FileNameWithType fileNameWithType1 = new FileNameWithType(new FileNameTest(test1[0]), FileType.FILE);
        FileNameWithType fileNameWithType2 = new FileNameWithType(new FileNameTest(test1[1]), FileType.FILE);

        int compare1 = tested.compare(fileNameWithType1, fileNameWithType2);
        int compare2 = tested.compare(fileNameWithType2, fileNameWithType1);

        Assert.assertNotEquals(compare1,compare2);
    }

    @Test
    public void testComparator2(){
        FileNameWithType fileNameWithType1 = new FileNameWithType(new FileNameTest(test2[0]), FileType.FILE);
        FileNameWithType fileNameWithType2 = new FileNameWithType(new FileNameTest(test2[1]), FileType.FILE);

        int compare1 = tested.compare(fileNameWithType1, fileNameWithType2);
        int compare2 = tested.compare(fileNameWithType2, fileNameWithType1);

        Assert.assertNotEquals(compare1,compare2);
    }


    class FileNameTest implements FileName{
        final String baseName;

        public FileNameTest(String baseName){
            this.baseName = baseName;
        }

        @Override public String getBaseName() {
            return baseName;
        }

        @Override public String getPath() {
            return null;
        }

        @Override public String getPathDecoded() throws FileSystemException {
            return null;
        }

        @Override public String getExtension() {
            return null;
        }

        @Override public int getDepth() {
            return 0;
        }

        @Override public String getScheme() {
            return null;
        }

        @Override public String getURI() {
            return null;
        }

        @Override public String getRootURI() {
            return null;
        }

        @Override public FileName getRoot() {
            return null;
        }

        @Override public FileName getParent() {
            return null;
        }

        @Override public String getRelativeName(FileName name) throws FileSystemException {
            return null;
        }

        @Override public boolean isAncestor(FileName ancestor) {
            return false;
        }

        @Override public boolean isDescendent(FileName descendent) {
            return false;
        }

        @Override public boolean isDescendent(FileName descendent, NameScope nameScope) {
            return false;
        }

        @Override public FileType getType() {
            return null;
        }

        @Override public String getFriendlyURI() {
            return null;
        }

        @Override public int compareTo(FileName fileName) {
            return 0;
        }
        @Override
        public String toString(){
            return baseName;
        }
    }
}