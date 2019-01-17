import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_nfc_tools/flutter_nfc_tools.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String response = "No error";

    try {
      FlutterNfcTools.init().then((m) {
        print("initialiazed : "+m);
      }).catchError((err) {
        print("err : "+err.toString());
      });
    } on PlatformException {
      response = 'Failed to scan tag.';
    }

    try {
      FlutterNfcTools.tagsEventsStream.listen((m) {
        print("tag scanned : "+m);
      });
    } on PlatformException {
      response = 'Failed to scan tag.';
    }
    try {
      FlutterNfcTools.ndefEventsStream.listen((m) {
        print("ndef scanned : "+m.toString());

        List<int> payload = m['ndefMessage'][0]['payload'].sublist(1).cast<int>().toList();
        print("payload : "+FlutterNfcTools.bytesToString(payload));
// {isWritable: true, maxSize: 137, canMakeReadOnly: true, type: NFC Forum Type 2, techTypes: [android.nfc.tech.MifareUltralight, android.nfc.tech.NfcA, android.nfc.tech.Ndef], id: [4, 55, -39, 74, -125, 86, -127], ndefMessage: [{id: [], type: [85], tnf: 1, payload: [4, 121, 105, 105, 46, 105, 115, 47, 55, 48, 105, 67, 77, 97, 63, 109, 61, 49]}]}
// yii.is/70iCMa?m=1
        FlutterNfcTools.writeTag(m['ndefMessage']);
      });
    } on PlatformException {
      response = 'Failed to scan ndef.';
    }
    try {
      FlutterNfcTools.ndefFormatableEventsStream.listen((m) {
        print("ndefFormatable scanned : "+m);
      });
    } on PlatformException {
      response = 'Failed to scan ndefFormatable.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = response;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Running on: $_platformVersion\n'),
        ),
      ),
    );
  }
}
