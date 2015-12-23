package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.TableFunctionImpl;
import org.apache.calcite.util.ImmutableBitSet;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.VoidProgressListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TableFunctions {

    public static Multimap<String, TableFunction> createAll() {
        ImmutableMultimap.Builder<String, TableFunction> builder = ImmutableMultimap.builder();
        builder.put("getValues", TableFunctionImpl.create(TableFunctions.class, "getValues"));
        builder.put("getRetainedSet", TableFunctionImpl.create(TableFunctions.class, "getRetainedSet"));
        builder.put("getOutboundReferences", TableFunctionImpl.create(TableFunctions.class, "getOutboundReferences"));
        builder.put("getInboundReferences", TableFunctionImpl.create(TableFunctions.class, "getInboundReferences"));
        return builder.build();
    }

    @SuppressWarnings("unused")
    public static QueryableTable getValues(Object r) {
        List<HeapReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference) r;
            try {
                ICollectionExtractor collectionExtractor = CollectionExtractionUtils.findCollectionExtractor(ref.getIObject());
                if (collectionExtractor == null) {
                    references = Collections.emptyList();
                }
                else {
                    references = collectReferences(ref.getIObject().getSnapshot(), collectionExtractor.extractEntryIds(ref.getIObject()));
                }
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract values from " + ref, e);
            }
        }
        return new HeapReferenceTable(references, false);
    }

    @SuppressWarnings("unused")
    public static QueryableTable getRetainedSet(Object r) {
        List<HeapReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference)r;
            ISnapshot snapshot = ref.getIObject().getSnapshot();
            try {
                references = collectReferences
                        (
                                snapshot,
                                snapshot.getRetainedSet(new int[]{ref.getIObject().getObjectId()}, new VoidProgressListener())
                        );
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract retained set from "+r, e);
            }
        }
        return new HeapReferenceTable(references, true);
    }

    @SuppressWarnings("unused")
    public static QueryableTable getOutboundReferences(Object r) {
        List<HeapReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference)r;
            ISnapshot snapshot = ref.getIObject().getSnapshot();
            try {
                references = collectReferences
                        (
                                snapshot,
                                snapshot.getOutboundReferentIds(ref.getIObject().getObjectId())
                        );
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract outbound references from "+r, e);
            }
        }
        return new HeapReferenceTable(references, true);
    }

    @SuppressWarnings("unused")
    public static QueryableTable getInboundReferences(Object r) {
        List<HeapReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference)r;
            ISnapshot snapshot = ref.getIObject().getSnapshot();
            try {
                references = collectReferences
                        (
                                snapshot,
                                snapshot.getInboundRefererIds(ref.getIObject().getObjectId())
                        );
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract inbound references from "+r, e);
            }
        }
        return new HeapReferenceTable(references, true);
    }

    private static List<HeapReference> collectReferences(ISnapshot snapshot, int[] objectIds) throws SnapshotException {
        if (objectIds != null && objectIds.length > 0) {
            List<HeapReference> references = new ArrayList<>();
            for(int objectId : objectIds) {
                references.add(HeapReference.valueOf(snapshot.getObject(objectId)));
            }
            return references;
        } else {
            return Collections.emptyList();
        }
    }

    private static class HeapReferenceTable extends AbstractQueryableTable {
        private final static List<ImmutableBitSet> UNIQUE_KEYS_STATISTICS = ImmutableList.of(ImmutableBitSet.of(1));
        private final static List<ImmutableBitSet> NON_UNIQUE_KEYS_STATISTICS = ImmutableList.of(ImmutableBitSet.of(0));

        private final Collection<HeapReference> references;
        private final boolean unique;

        public HeapReferenceTable(Collection<HeapReference> references, boolean unique) {
            super(HeapReference.class);
            this.references = references;
            this.unique = unique;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schemaPlus, String s) {
            BaseQueryable<HeapReference> queryable = new BaseQueryable<HeapReference>(null, HeapReference.class, null) {
                @Override
                public Enumerator<HeapReference> enumerator() {
                    return Linq4j.enumerator(references);
                }
            };
            return (Queryable<T>) queryable;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory relDataTypeFactory) {
            return relDataTypeFactory.builder().add("this",  relDataTypeFactory.createJavaType(HeapReference.class)).build();
        }

        @Override
        public Statistic getStatistic() {
            return Statistics.of(references.size(), unique ? UNIQUE_KEYS_STATISTICS : NON_UNIQUE_KEYS_STATISTICS);
        }
    }
}

