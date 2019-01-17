import 'dart:async';

import 'package:flutter/services.dart';

class FlutterNfcToolsNdef {
  String ndef;
  String email;

  FlutterNfcToolsNdef.fromJson(Map json){
    this.ndef = json["Name"];
    this.email = json ["Email"];
  }
}

class FlutterNfcTnf {
  int value;
  bool mb;
  bool me;
  bool cf;
  bool sr;
  bool il;
  int tnf;

  /**
   * Decode the bit flags from a TNF Byte.
   *
   * @returns object with decoded data
   *
   *  See NFC Data Exchange Format (NDEF) Specification Section 3.2 RecordLayout
   */
  FlutterNfcTnf.decode(this.value) {
    mb = (value & 0x80) != 0;
    me = (value & 0x40) != 0;
    cf = (value & 0x20) != 0;
    sr = (value & 0x10) != 0;
    il = (value & 0x8) != 0;
    tnf = (value & 0x7);
  }

  /**
   * Encode NDEF bit flags into a TNF Byte.
   *
   * @returns tnf byte
   *
   *  See NFC Data Exchange Format (NDEF) Specification Section 3.2 RecordLayout
   */
  FlutterNfcTnf.encode(this.mb, this.me, this.cf, this.sr, this.il, this.tnf) {
    value = tnf;
    if (mb) {
      value = value | 0x80;
    }
    if (me) {
      value = value | 0x40;
    }
    // note if cf: me, mb, li must be false and tnf must be 0x6
    if (cf) {
      value = value | 0x20;
    }
    if (sr) {
      value = value | 0x10;
    }
    if (il) {
      value = value | 0x8;
    }
  }
}

enum FlutterNfcToolsTnfType {
  empty,
  wellKnown,
  mimeMedia,
  absoluteUri,
  externalType,
  unknown,
  unchanged,
  reserved
}

enum FlutterNfcToolsRtd {
  text,
  uri,
  smartPoster,
  alternativeCarrier,
  handoverCarrier,
  handoverRequest,
  handoverSelect
}


class FlutterNfcNdefPayload {

  /*
  as bytes
  as string
  first byte as type ?
  */
}
class FlutterNfcNdefRecord {
  FlutterNfcTnf tnf;
  String type;
  String id;
  FlutterNfcNdefPayload payload;
}

class FlutterNfcMessage {
  List<FlutterNfcNdefRecord> records;
}




class FlutterNfcTools {
  static const MethodChannel _methodChannel =
      const MethodChannel('flutter_nfc_tools/methods');
  static const EventChannel _eventsNdefChannel =
      const EventChannel('flutter_nfc_tools/eventsNdef');
  static const EventChannel _eventsNdefFormatableChannel =
      const EventChannel('flutter_nfc_tools/eventsNdefFormatable');
  static const EventChannel _eventsMimetypeChannel =
      const EventChannel('flutter_nfc_tools/eventsMimetype');
  static const EventChannel _eventsTagChannel =
      const EventChannel('flutter_nfc_tools/eventsTag');

  static Future<Null> showSettings() async {
    await _methodChannel.invokeMethod('showSettings');
  }
  static Future<Null> registerMimeType(String mimeType) async {
    await _methodChannel.invokeMethod('registerMimeType', [mimeType]);
  }
  static Future<Null> removeMimeType(String mimeType) async {
    await _methodChannel.invokeMethod('removeMimeType', [mimeType]);
  }
  static Future<Null> writeTag(dynamic ndefMessage) async {
    await _methodChannel.invokeMethod('writeTag', [ndefMessage.toString()]);
  }
  static Future<Null> makeReadOnly() async {
    await _methodChannel.invokeMethod('makeReadOnly');
  }
  static Future<Null> eraseTag() async {
    await _methodChannel.invokeMethod('eraseTag');
  }
  static Future<Null> init() async {
    await _methodChannel.invokeMethod('init');
  }
  static Future<Null> enabled() async {
    await _methodChannel.invokeMethod('enabled');
  }
  static Future<Null> connect(String tech, int timeout) async {
    await _methodChannel.invokeMethod('connect', [tech, timeout]);
  }
  static Future<Null> close() async {
    await _methodChannel.invokeMethod('close');
  }

  static Stream<dynamic> get tagsEventsStream => _eventsTagChannel.receiveBroadcastStream();
  static Stream<dynamic> get ndefEventsStream => _eventsNdefChannel.receiveBroadcastStream();
  static Stream<dynamic> get ndefFormatableEventsStream => _eventsNdefFormatableChannel.receiveBroadcastStream();
  static Stream<dynamic> get mimetypeEventsStream => _eventsMimetypeChannel.receiveBroadcastStream();


  static String bytesToString(List<int> bytes) {
    // based on http://ciaranj.blogspot.fr/2007/11/utf8-characters-encoding-in-javascript.html

    String result = "";
    int i, c, c1, c2, c3;
    i = c = c1 = c2 = c3 = 0;

    // Perform byte-order check.
    if( bytes.length >= 3 ) {
      if( (bytes[0] & 0xef) == 0xef && (bytes[1] & 0xbb) == 0xbb && (bytes[2] & 0xbf) == 0xbf ) {
        // stream has a BOM at the start, skip over
        i = 3;
      }
    }

    while ( i < bytes.length ) {
      c = bytes[i] & 0xff;

      if ( c < 128 ) {

        result += String.fromCharCode(c);
        i++;

      } else if ( (c > 191) && (c < 224) ) {

        if ( i + 1 >= bytes.length ) {
          throw "Un-expected encoding error, UTF-8 stream truncated, or incorrect";
        }
        c2 = bytes[i + 1] & 0xff;
        result += String.fromCharCode( ((c & 31) << 6) | (c2 & 63) );
        i += 2;

      } else {

        if ( i + 2 >= bytes.length  || i + 1 >= bytes.length ) {
          throw "Un-expected encoding error, UTF-8 stream truncated, or incorrect";
        }
        c2 = bytes[i + 1] & 0xff;
        c3 = bytes[i + 2] & 0xff;
        result += String.fromCharCode( ((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63) );
        i += 3;

      }
    }
    return result;
  }

  static List<int> stringToBytes(String string) {
    // based on http://ciaranj.blogspot.fr/2007/11/utf8-characters-encoding-in-javascript.html

    List<int> bytes = [];

    for (int n = 0; n < string.length; n++) {

      int c = string.codeUnitAt(n);

      if (c < 128) {

        bytes[bytes.length]= c;

      } else if((c > 127) && (c < 2048)) {

        bytes[bytes.length] = (c >> 6) | 192;
        bytes[bytes.length] = (c & 63) | 128;

      } else {

        bytes[bytes.length] = (c >> 12) | 224;
        bytes[bytes.length] = ((c >> 6) & 63) | 128;
        bytes[bytes.length] = (c & 63) | 128;

      }
    }

    return bytes;
  }

  // see android.nfc.NdefRecord for documentation about constants
  // http://developer.android.com/reference/android/nfc/NdefRecord.html
  static const int  TNF_EMPTY = 0x0,
                    TNF_WELL_KNOWN= 0x01,
                    TNF_MIME_MEDIA= 0x02,
                    TNF_ABSOLUTE_URI= 0x03,
                    TNF_EXTERNAL_TYPE= 0x04,
                    TNF_UNKNOWN= 0x05,
                    TNF_UNCHANGED= 0x06,
                    TNF_RESERVED= 0x07;

  static const List<int>  RTD_TEXT = [0x54], // "T"
                          RTD_URI= [0x55], // "U"
                          RTD_SMART_POSTER= [0x53, 0x70], // "Sp"
                          RTD_ALTERNATIVE_CARRIER= [0x61, 0x63], // "ac"
                          RTD_HANDOVER_CARRIER= [0x48, 0x63], // "Hc"
                          RTD_HANDOVER_REQUEST= [0x48, 0x72], // "Hr"
                          RTD_HANDOVER_SELECT= [0x48, 0x73]; // "Hs"

  /**
   * Creates a JSON representation of a NDEF Record.
   *
   * @tnf 3-bit TNF (Type Name Format) - use one of the TNF_* constants
   * @type byte array, containing zero to 255 bytes, must not be null
   * @id byte array, containing zero to 255 bytes, must not be null
   * @payload byte array, containing zero to (2 ** 32 - 1) bytes, must not be null
   *
   * @returns JSON representation of a NDEF record
   *
   * @see textRecord, uriRecord and mimeMediaRecord for examples
   */

  static Map<String, Object> record(int tnf, List<int> type, List<int> id, String payload) {
    return recordFromBytes(tnf, type, id, stringToBytes(payload));
  }

  static Map<String, Object> recordFromBytes(int tnf, List<int> type, List<int> id, List<int> payload) {

    // handle null values
    //if (!tnf) { tnf = TNF_EMPTY; }
    if (type == null) { type = []; }
    if (id == null) { id = []; }
    if (payload == null) { payload = []; }

    return {
      'tnf': tnf,
      'type': type,
      'id': id,
      'payload': payload
    };
  }

  /**
   * Helper that creates an NDEF record containing plain text.
   *
   * @text String of text to encode
   * @languageCode ISO/IANA language code. Examples: “fi”, “en-US”, “fr- CA”, “jp”. (optional)
   * @id byte[] (optional)
   */
  static Map<String, Object> textRecord(String text, String languageCode, List<int> id) {
    List<int> payload = encodePayload(text, languageCode);
    if (id == null) { id = []; }
    return recordFromBytes(TNF_WELL_KNOWN, RTD_TEXT, id, payload);
  }

  /**
   * Helper that creates a NDEF record containing a URI.
   *
   * @uri String
   * @id byte[] (optional)
   */
  static Map<String, Object> uriRecord (uri, id) {
    List<int> payload = encodePayload(uri, null);
    if (!id) { id = []; }
    return recordFromBytes(TNF_WELL_KNOWN, RTD_URI, id, payload);
  }

  /**
   * Helper that creates a NDEF record containing an absolute URI.
   *
   * An Absolute URI record means the URI describes the payload of the record.
   *
   * For example a SOAP message could use "http://schemas.xmlsoap.org/soap/envelope/"
   * as the type and XML content for the payload.
   *
   * Absolute URI can also be used to write LaunchApp records for Windows.
   *
   * See 2.4.2 Payload Type of the NDEF Specification
   * http://www.nfc-forum.org/specs/spec_list#ndefts
   *
   * Note that by default, Android will open the URI defined in the type
   * field of an Absolute URI record (TNF=3) and ignore the payload.
   * BlackBerry and Windows do not open the browser for TNF=3.
   *
   * To write a URI as the payload use uriRecord(uri)
   *
   * @uri String
   * @payload byte[] or String
   * @id byte[] (optional)
   */
  static Map<String, Object> absoluteUriRecord (uri, payload, id) {
    if (!id) { id = []; }
    if (!payload) { payload = []; }
    return record(TNF_ABSOLUTE_URI, uri, id, payload);
  }

  /**
   * Helper that creates a NDEF record containing an mimeMediaRecord.
   *
   * @mimeType String
   * @payload byte[]
   * @id byte[] (optional)
   */
  static Map<String, Object> mimeMediaRecord (mimeType, payload, id) {
    if (!id) { id = []; }
    return record(TNF_MIME_MEDIA, stringToBytes(mimeType), id, payload);
  }

  /**
   * Helper that creates an NDEF record containing an Smart Poster.
   *
   * @ndefRecords array of NDEF Records
   * @id byte[] (optional)
   */
  static Map<String, Object> smartPoster (ndefRecords, id) {
    List<int> payload = [];

    if (!id) { id = []; }

    if (ndefRecords)
    {
      // make sure we have an array of something like NDEF records before encoding
      if (ndefRecords[0] is Object && ndefRecords[0].hasOwnProperty('tnf')) {
        payload = encodeMessage(ndefRecords);
      } else {
        // assume the caller has already encoded the NDEF records into a byte array
        payload = ndefRecords;
      }
    } else {
      print("WARNING: Expecting an array of NDEF records");
    }

    return recordFromBytes(TNF_WELL_KNOWN, RTD_SMART_POSTER, id, payload);
  }

  /**
   * Helper that creates an empty NDEF record.
   *
   */
  static Map<String, Object> emptyRecord() {
    return recordFromBytes(TNF_EMPTY, [], [], []);
  }

  /**
   * Helper that creates an Android Application Record (AAR).
   * http://developer.android.com/guide/topics/connectivity/nfc/nfc.html#aar
   *
   */
  static Map<String, Object> androidApplicationRecord(String packageName) {
    return record(TNF_EXTERNAL_TYPE, stringToBytes("android.com:pkg"), [], packageName);
  }


  static String decodePayload(List<int> data) {

    int languageCodeLength = (data[0] & 0x3F); // 6 LSBs
    List<int> languageCode = data.sublist(1, 1 + languageCodeLength);
    bool utf16 = (data[0] & 0x80) != 0; // assuming UTF-16BE

    // TODO need to deal with UTF in the future
    if (utf16) {
      print("WARNING: utf-16 data may not be handled properly for " +bytesToString(languageCode));
    }
    // Use TextDecoder when we have enough browser support
    // new TextDecoder('utf-8').decode(data.sublist(languageCodeLength + 1));
    // new TextDecoder('utf-16').decode(data.sublist(languageCodeLength + 1));

    return bytesToString(data.sublist(languageCodeLength + 1));
  }

  // encode text payload
  // @returns an array of bytes
  static List<int> encodePayload(String text, String lang) {
// manage uri type here ?
  /*
    Value    Protocol
    -----    --------
    0x00     No prepending is done ... the entire URI is contained in the URI Field
    0x01     http://www.
    0x02     https://www.
    0x03     http://
    0x04     https://
    0x05     tel:
    0x06     mailto:
    0x07     ftp://anonymous:anonymous@
    0x08     ftp://ftp.
    0x09     ftps://
    0x0A     sftp://
  */


    // ISO/IANA language code, but we're not enforcing
    lang = lang ?? 'en';

    List<int> encoded = stringToBytes(lang + text);
    encoded.insert(0, lang.length);

    return encoded;
  }


  /**
   * Encodes an NDEF Message into bytes that can be written to a NFC tag.
   *
   * @ndefRecords an Array of NDEF Records
   *
   * @returns byte array
   *
   * @see NFC Data Exchange Format (NDEF) http://www.nfc-forum.org/specs/spec_list/
   */
  static List<int> encodeMessage (List<dynamic> ndefRecords) {

    List<int> encoded = [];
    int tnf_byte,
        type_length,
        payload_length,
        id_length,
        i;
    bool mb, me, // messageBegin, messageEnd
        cf = false, // chunkFlag TODO implement
        sr, // boolean shortRecord
        il; // boolean idLengthFieldIsPresent

    for(i = 0; i < ndefRecords.length; i++) {

      mb = (i == 0);
      me = (i == (ndefRecords.length - 1));
      sr = (ndefRecords[i].payload.length < 0xFF);
      il = (ndefRecords[i].id.length > 0);
      tnf_byte = FlutterNfcTnf.encode(mb, me, cf, sr, il, ndefRecords[i].tnf).value;
      encoded.add(tnf_byte);

      type_length = ndefRecords[i].type.length;
      encoded.add(type_length);

      if (sr) {
      payload_length = ndefRecords[i].payload.length;
      encoded.add(payload_length);
      } else {
      payload_length = ndefRecords[i].payload.length;
      // 4 bytes
      encoded.add((payload_length >> 24));
      encoded.add((payload_length >> 16));
      encoded.add((payload_length >> 8));
      encoded.add((payload_length & 0xFF));
      }

      if (il) {
      id_length = ndefRecords[i].id.length;
      encoded.add(id_length);
      }

      encoded.addAll(ndefRecords[i].type);

      if (il) {
        encoded.addAll(ndefRecords[i].id);
      }

      encoded.addAll(ndefRecords[i].payload);
    }

    return encoded;
  }

  /**
   * Decodes an array bytes into an NDEF Message
   *
   * @bytes an array bytes read from a NFC tag
   *
   * @returns array of NDEF Records
   *
   * @see NFC Data Exchange Format (NDEF) http://www.nfc-forum.org/specs/spec_list/
   */
  static List<Map> decodeMessage(List<int> ndefBytes) {

    int tnf_byte;
    int type_length = 0,
        payload_length = 0,
        id_length = 0;
    List<int> bytes = ndefBytes.sublist(0), // clone since parsing is destructive
        record_type = [],
        id = [],
        payload = [];
    List<dynamic> ndef_message = [];
    FlutterNfcTnf header;

    while(bytes.length>0) {
      tnf_byte = bytes.removeAt(0);
      header = FlutterNfcTnf.decode(tnf_byte);

      type_length = bytes.removeAt(0);

      if (header.sr) {
        payload_length = bytes.removeAt(0);
      } else {
        // next 4 bytes are length
        payload_length = ((0xFF & bytes.removeAt(0)) << 24) |
        ((0xFF & bytes.removeAt(0)) << 26) |
        ((0xFF & bytes.removeAt(0)) << 8) |
        (0xFF & bytes.removeAt(0));
      }

      if (header.il) {
        id_length = bytes.removeAt(0);
      }

      record_type = bytes.sublist(0, type_length);
      id = bytes.sublist(0, id_length);
      payload = bytes.sublist(0, payload_length);

      ndef_message.add(
          recordFromBytes(header.tnf, record_type, id, payload)
      );

      if (header.me) { break; } // last message
    }

    return ndef_message;
  }
}
