/*
 * Copyright (C) 2017 seiginonakama (https://github.com/seiginonakama).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.seiginonakama.res

import pink.madis.apk.arsc.*

import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 1. modify app PackageChunk id
 * 2. modify all 0x7f res id in app PackageChunk
 * 3. add LibraryChunk(DynamicRefTable) for PackageChunk, because android 5.0+ don'y recognize customPackageId
 *
 * author: zhoulei date: 2017/6/2.
 */
public class ResourceChunkProcessor {
    private final int customPackageId;

    public ResourceChunkProcessor(int pkgId) {
        customPackageId = pkgId;
    }

    public void processChunkFiles(File chunkRoot) {
        chunkRoot.eachFileRecurse {
            file ->
                if (file.name == 'resources.arsc') {
                    processResourceTable(file);
                } else if (file.name.endsWith('.xml')) {
                    processXml(file);
                }
        }
    }

    private void processResourceTable(File arsc) {
        ResourceFile resourceFile = ResourceFile.fromInputStream(arsc.newDataInputStream())
        List<Chunk> chunks = resourceFile.getChunks();
        for (Chunk chunk : chunks) {
            if (chunk instanceof ResourceTableChunk) {
                ResourceTableChunk tableChunk = chunk;
                Map<Integer, Chunk> chunkMap = tableChunk.chunks
                for (Map.Entry<Integer, Chunk> entry : chunkMap) {
                    Chunk c = entry.getValue();

                    if (c instanceof PackageChunk && c.id == 0x7f) {
                        PackageChunk packageChunk = c;

                        //只处理App PackageChunk
                        byte[] libraryChunkBytes = createLibraryChunk(packageChunk.packageName).toByteArray()
                        byte[] packageChunkBytes = packageChunk.toByteArray();
                        byte[] newPackageChunkBytes = new byte[packageChunkBytes.length + libraryChunkBytes.length]
                        //type + header + payload
                        System.arraycopy(packageChunkBytes, 0, newPackageChunkBytes, 0, packageChunkBytes.size())

                        ByteBuffer buffer = ByteBuffer.wrap(newPackageChunkBytes).order(ByteOrder.LITTLE_ENDIAN);
                        buffer.getShort() //skip type
                        buffer.getShort() //skip header size
                        buffer.putInt(newPackageChunkBytes.length) //rewrite chunk size
                        buffer.putInt(customPackageId) //rewrite package id
                        buffer.position(packageChunkBytes.length)
                        buffer.put(libraryChunkBytes) //write library chunk
                        buffer.rewind()

                        buffer.getShort() //skip type
                        PackageChunk newPackageChunk = new PackageChunk(buffer, null)
                        try {
                            newPackageChunk.init(buffer)
                        } catch (IllegalStateException e) {
                            // android-chunk-utils still don't know LibraryChunk
                            // catch IllegalStateException(
                            //    String.format("PackageChunk contains an unexpected chunk: %s", chunk.getClass()));
                        }
                        entry.setValue(newPackageChunk)

                        processTypeChunks(newPackageChunk.typeChunks)
                    }
                }
            }
        }
        arsc.delete()
        DataOutputStream output = arsc.newDataOutputStream();
        output.write(resourceFile.toByteArray())
        output.flush()
        output.close()
    }

    private void processTypeChunks(Collection<TypeChunk> typeChunks) {
        for (TypeChunk chunk : typeChunks) {
            Field field = chunk.getClass().getDeclaredField('entries')
            field.setAccessible(true)
            Map<Integer, TypeChunk.Entry> entries = field.get(chunk);
            for (Map.Entry<Integer, TypeChunk.Entry> entry : entries) {
                TypeChunk.Entry e = entry.value;
                TypeChunk.Entry autoValue_typeChunk_entry
                if (e.isComplex()) {
                    Map<Integer, ResourceValue> values = e.values();
                    int parentEntry = e.parentEntry();
                    if (isAppPackage(parentEntry)) {
                        //modify parent id
                        parentEntry = remakePackage(e.parentEntry(), customPackageId)
                    }

                    //修改attr的key的资源id
                    Set<Map.Entry<Integer, ResourceValue>> valueEntries = values.entrySet()
                    Set<Map.Entry<Integer, ResourceValue>> backup = new HashSet<>()
                    Iterator iterator = valueEntries.iterator()
                    while (iterator.hasNext()) {
                        Map.Entry<Integer, ResourceValue> valueEntry = iterator.next()
                        if (isAppPackage(valueEntry.key)) {
                            iterator.remove()
                            backup.add(valueEntry)
                        }
                    }
                    for (Map.Entry<Integer, ResourceValue> entryValue : backup) {
                        //modify bag key id
                        int key = entryValue.getKey();
                        values.put(remakePackage(key, customPackageId), entryValue.getValue())
                    }

                    //修改attr的资源值id
                    for (Map.Entry<Integer, ResourceValue> valueEntry : values) {
                        ResourceValue resourceValue = processResourceValue(valueEntry.value);
                        valueEntry.setValue(resourceValue)
                    }
                    autoValue_typeChunk_entry = new AutoValue_TypeChunk_Entry(
                            e.headerSize(), e.flags(), e.keyIndex(), processResourceValue(e.value()), e.values(), parentEntry, e.parent()
                    )
                } else {
                    autoValue_typeChunk_entry = new AutoValue_TypeChunk_Entry(
                            e.headerSize(), e.flags(), e.keyIndex(), processResourceValue(e.value()), e.values(), e.parentEntry(), e.parent()
                    )
                }
                entry.setValue(autoValue_typeChunk_entry)
            }
        }
    }

    /**
     * modify res id
     */
    private ResourceValue processResourceValue(ResourceValue resourceValue) {
        if (resourceValue == null) {
            return null;
        }
        ResourceValue.Type type = resourceValue.type();
        if (type == ResourceValue.Type.REFERENCE || type == ResourceValue.Type.ATTRIBUTE
                || type == ResourceValue.Type.DYNAMIC_REFERENCE) {
            if (isAppPackage(resourceValue.data())) {
                ResourceValue newValue = new AutoValue_ResourceValue(resourceValue.size(),
                        resourceValue.type(), remakePackage(resourceValue.data(), customPackageId))
                return newValue;
            }
        }
        return resourceValue;
    }

    private void processXml(File xml) {
        ResourceFile resourceFile = ResourceFile.fromInputStream(xml.newDataInputStream());
        File tmpFile = new File(xml.absolutePath + '.tmp')
        DataOutputStream dataOutputStream = tmpFile.newDataOutputStream()
        List<Chunk> chunks = resourceFile.getChunks();
        for (Chunk chunk : chunks) {
            processXmlStartElementChunk(chunk)
            processXmlResourceMapChunk(chunk)
            dataOutputStream.write(chunk.toByteArray())
        }
        dataOutputStream.flush()
        dataOutputStream.close()
        xml.delete()
        tmpFile.renameTo(xml)
    }

    private void processXmlStartElementChunk(Chunk chunk) {
        if (chunk instanceof XmlStartElementChunk) {
            XmlStartElementChunk startElementChunk = chunk;
            Field field = startElementChunk.getClass().getDeclaredField("attributes");
            field.setAccessible(true)
            List<XmlAttribute> attributes = field.get(startElementChunk)
            List<XmlAttribute> processedAttr = new ArrayList<>();
            for (XmlAttribute attr : attributes) {
                ResourceValue processedValue = processResourceValue(attr.typedValue())
                processedAttr.add(new AutoValue_XmlAttribute(attr.namespaceIndex(), attr.nameIndex(),
                        attr.rawValueIndex(), processedValue, attr.parent()))
            }
            attributes.clear()
            attributes.addAll(processedAttr)
        } else {
            if (chunk instanceof ChunkWithChunks) {
                Collection<Chunk> children = chunk.getChunks().values()
                for (Chunk child : children) {
                    processXmlStartElementChunk(child)
                }
            }
        }
    }

    private void processXmlResourceMapChunk(Chunk chunk) {
        if (chunk instanceof XmlResourceMapChunk) {
            XmlResourceMapChunk xmlResourceMapChunk = chunk;
            List<Integer> remappedResIds = new ArrayList<>();
            for (Integer resId : xmlResourceMapChunk.resources) {
                if (isAppPackage(resId)) {
                    remappedResIds.add(remakePackage(resId, customPackageId))
                } else {
                    remappedResIds.add(resId)
                }
            }
            xmlResourceMapChunk.resources.clear()
            xmlResourceMapChunk.resources.addAll(remappedResIds)
        } else {
            if (chunk instanceof ChunkWithChunks) {
                Collection<Chunk> children = chunk.getChunks().values()
                for (Chunk child : children) {
                    processXmlResourceMapChunk(child)
                }
            }
        }
    }

    private LibraryChunk createLibraryChunk(String packageName) {
        final short headerSize = 12 //type(2) + headerSize(2) + chunkSize(4) + entrySize(4)
        final int libChunkSize = headerSize + LibraryChunk.Entry.SIZE; //headerSize + 1 * Entry.SIZE
        ByteBuffer libByteBuffer = ByteBuffer.allocate(libChunkSize);
        libByteBuffer.putShort(Chunk.Type.TABLE_LIBRARY.code()) //type
        libByteBuffer.putShort(headerSize) //headerSize
        libByteBuffer.putInt(libChunkSize) //chunkSize
        libByteBuffer.putInt(1) //entrySize
        libByteBuffer.putInt(customPackageId) //packageId
        PackageUtils.writePackageName(libByteBuffer, packageName)
        libByteBuffer.rewind()

        return Chunk.newInstance(libByteBuffer)
    }

    private static boolean isAppPackage(int resId) {
        return (resId & 0xFF000000) == 0x7F000000;
    }

    private static int remakePackage(int resId, int packageId) {
        return (resId & 0x00FFFFFF) | (packageId << 24)
    }
}
