/**
 * Content Provider to expose the Quuppa Tag ID to other apps on the same device.
 * 
 * Other apps can query the Tag ID using:
 * 
 *   Cursor cursor = getContentResolver().query(
 *       Uri.parse("content://com.quuppa.quuppatag.provider/tagid"),
 *       null, null, null, null);
 *   if (cursor != null && cursor.moveToFirst()) {
 *       String tagId = cursor.getString(cursor.getColumnIndexOrThrow("tag_id"));
 *       cursor.close();
 *   }
 * 
 * Copyright 2025 Quuppa Oy
 */
package com.quuppa.quuppatag;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.quuppa.tag.QuuppaTag;

public class QuuppaTagContentProvider extends ContentProvider {

    /** The authority for this content provider */
    public static final String AUTHORITY = "com.quuppa.quuppatag.provider";

    /** The content URI for querying the tag ID */
    public static final Uri CONTENT_URI_TAG_ID = Uri.parse("content://" + AUTHORITY + "/tagid");

    /** Column name for the tag ID in query results */
    public static final String COLUMN_TAG_ID = "tag_id";

    private static final int TAG_ID = 1;
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, "tagid", TAG_ID);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (uriMatcher.match(uri) == TAG_ID) {
            // Get or initialize the Tag ID using the Quuppa library
            String tagId = QuuppaTag.getOrInitTagId(getContext());

            // Create a cursor with the tag ID
            MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_TAG_ID});
            cursor.addRow(new Object[]{tagId});
            return cursor;
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        if (uriMatcher.match(uri) == TAG_ID) {
            return "vnd.android.cursor.item/vnd.quuppa.tagid";
        }
        return null;
    }

    // This is a read-only provider, so these methods are not implemented
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("This is a read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("This is a read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("This is a read-only provider");
    }
}

