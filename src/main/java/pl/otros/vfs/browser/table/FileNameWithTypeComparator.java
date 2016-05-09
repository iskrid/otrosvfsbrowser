/*
 * Copyright 2013 Krzysztof Otrebski (otros.systems@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.otros.vfs.browser.table;

import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileType;
import pl.otros.vfs.browser.ParentFileObject;

import javax.swing.*;
import java.util.Comparator;

public class FileNameWithTypeComparator implements Comparator<FileNameWithType> {
    private SortOrder sortOrder = SortOrder.ASCENDING;

    @Override public int compare(FileNameWithType o1, FileNameWithType o2) {
        return compareTo(o1, o2);
    }

    public int compareTo(FileNameWithType o1, FileNameWithType o2) {

        if (o1 == null || o1.getFileType() == null || o1.getFileName() == null) {
            return -1;
        }
        if (o2 == null || o2.getFileType() == null || o2.getFileName() == null) {
            return 1;
        }
        //folders first
        boolean folder1 = FileType.FOLDER.equals(o1.getFileType());
        boolean folder2 = FileType.FOLDER.equals(o2.getFileType());
        int result = 0;

        int sortOrderSign = SortOrder.ASCENDING.equals(sortOrder) ? 1 : -1;
        if (o1.getFileName().getBaseName().equalsIgnoreCase(ParentFileObject.PARENT_NAME)) {
            result += (-1 * sortOrderSign);
        } else if (o2.getFileName().getBaseName().equalsIgnoreCase(ParentFileObject.PARENT_NAME)) {
            result += (1 * sortOrderSign);
        } else if (folder1 && !folder2) {
            result += (-1 * sortOrderSign);
        } else if (!folder1 && folder2) {
            result += (1 * sortOrderSign);
        } else
        {
            AlphanumComparator alphanumComparator = new AlphanumComparator();
            result = alphanumComparator.compare(o1.getFileName().getBaseName(), o2.getFileName().getBaseName());
        }

//        else{
//            result = o1.getFileName().compareTo(o2.getFileName());
//        }

        System.out.println(o1.getFileName()+"-"+o2.getFileName()+"  "+result);

        if(result>0){
            result=1;
        }
        else if(result<0){
            result = -1;
        }

        return result;
    }

    public static boolean isNumber(String s, int radix) {
        if (s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 && s.charAt(i) == '-') {
                if (s.length() == 1)
                    return false;
                else
                    continue;
            }
            if (Character.digit(s.charAt(i), radix) < 0)
                return false;
        }

        return true;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }
}
