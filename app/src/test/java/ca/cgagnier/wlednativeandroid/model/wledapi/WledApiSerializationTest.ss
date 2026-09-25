╔═ test DeviceStateInfo full deserialization with real WLED 16_0_1 data ═╗
{
  "state": {
    "on": true,
    "bri": 195,
    "transition": 7,
    "ps": -1,
    "pl": -1,
    "nl": {
      "on": false,
      "dur": 60,
      "mode": 1,
      "tbri": 0,
      "rem": -1
    },
    "lor": 0,
    "mainseg": 0,
    "seg": [
      {
        "id": 0,
        "start": 0,
        "stop": 88,
        "len": 88,
        "grp": 1,
        "spc": 0,
        "on": true,
        "bri": 255,
        "col": [
          [
            0,
            17,
            255,
            0
          ],
          [
            144,
            79,
            255,
            0
          ],
          [
            0,
            0,
            0,
            0
          ]
        ],
        "fx": 107,
        "sx": 20,
        "ix": 144,
        "pal": 3,
        "sel": false,
        "rev": false,
        "mi": false
      },
      {
        "id": 1,
        "start": 88,
        "stop": 177,
        "len": 89,
        "grp": 1,
        "spc": 0,
        "on": true,
        "bri": 255,
        "col": [
          [
            99,
            0,
            0,
            0
          ],
          [
            0,
            0,
            0,
            0
          ],
          [
            0,
            0,
            0,
            0
          ]
        ],
        "fx": 0,
        "sx": 128,
        "ix": 128,
        "pal": 0,
        "sel": true,
        "rev": false,
        "mi": false
      }
    ]
  },
  "info": {
    "leds": {
      "count": 277,
      "pwr": 2171,
      "fps": 43,
      "maxpwr": 10002,
      "maxseg": 32
    },
    "wifi": {
      "bssid": "aa:bb:cc:dd:ee:ff",
      "rssi": -72,
      "signal": 56,
      "channel": 1,
      "ap": false
    },
    "ver": "16.0.1",
    "vid": 2606300,
    "cn": "Niji",
    "release": "ESP32",
    "repo": "wled/WLED",
    "name": "WLED Desk",
    "udpport": 21324,
    "simplifiedui": false,
    "live": false,
    "liveseg": -1,
    "ws": 3,
    "fxcount": 220,
    "palcount": 73,
    "cpalcount": 1,
    "fs": {
      "u": 32,
      "t": 983,
      "pmt": 1788492069
    },
    "arch": "esp32",
    "core": "4.4.8.240628",
    "clock": 240,
    "flash": 4,
    "freeheap": 120932,
    "uptime": 2252733,
    "time": "2026-9-22, 23:30:48",
    "opt": 79,
    "brand": "WLED",
    "product": "FOSS",
    "mac": "aabbccddeeff",
    "ip": "192.168.1.100"
  }
}
╔═ test State with multiple segments and nightlight serialization ═╗
{
  "on": true,
  "bri": 195,
  "transition": 7,
  "nl": {
    "on": false,
    "dur": 60,
    "mode": 1,
    "tbri": 0,
    "rem": -1
  },
  "seg": [
    {
      "id": 0,
      "start": 0,
      "stop": 88,
      "len": 88,
      "grp": 1,
      "spc": 0,
      "on": true,
      "bri": 255,
      "col": [
        [
          0,
          17,
          255,
          0
        ],
        [
          144,
          79,
          255,
          0
        ]
      ],
      "fx": 107,
      "sx": 20,
      "ix": 144,
      "pal": 3
    }
  ]
}
╔═ [end of file] ═╗
