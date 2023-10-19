#!/usr/bin/python

import sys
import re
import io
import zipfile
import pydub

VOICES = [
    [ 1, "ARRIVED.mp3", "目的地に到着しました、お疲れさまでした"],
    [ 2, "CONTINUE_ON_THE_RAMP.mp3", "分岐です"],
    [ 3, "CONTINUE_STRAIGHT.mp3", "直進です"],
    [ 4, "DATA_LOST.mp3", "データ接続が失われました"],
    [ 5, "DESTINATION_ON_LEFT.mp3", "目的地は左側です、お疲れさまでした"],
    [ 6, "DESTINATION_ON_RIGHT.mp3", "目的地は右側です、お疲れさまでした"],
    [ 7, "DESTINATION_WILL_BE_ON_LEFT.mp3", "目的地は左側です"],
    [ 8, "DESTINATION_WILL_BE_ON_RIGHT.mp3", "目的地は右側です"],
    [ 9, "ENTER_THE_ROUNDABOUT.mp3", "ロータリー入口です"],
    [ 10, "EXIT_THE_ROUNDABOUT.mp3", "ロータリー出口です"],
    [ 11, "GENERIC_CONTINUE.mp3", "しばらく道なりです"],
    [ 12, "GPS_LOST.mp3", "GPS信号が失われました"],
    [ 13, "HEAD_EAST.mp3", "東に進みます"],
    [ 14, "HEAD_NORTH.mp3", "北に進みます"],
    [ 15, "HEAD_NORTHEAST.mp3", "北東に進みます"],
    [ 16, "HEAD_NORTHWEST.mp3", "北西に進みます"],
    [ 17, "HEAD_SOUTH.mp3", "南に進みます"],
    [ 18, "HEAD_SOUTHEAST.mp3", "南東に進みます"],
    [ 19, "HEAD_SOUTHWEST.mp3", "南西に進みます"],
    [ 20, "HEAD_WEST.mp3", "西に進みます"],
    [ 21, "IN_1000_FEET.mp3", "およそ1000フィート先"],
    [ 22, "IN_100_FEET.mp3", "およそ100フィート先"],
    [ 23, "IN_100_METERS.mp3", "およそ100メートル先"],
    [ 24, "IN_100_YARDS.mp3", "およそ100ヤード先"],
    [ 25, "IN_150_FEET.mp3", "およそ150フィート先"],
    [ 26, "IN_150_METERS.mp3", "およそ150メートル先"],
    [ 27, "IN_150_YARDS.mp3", "およそ150ヤード先"],
    [ 28, "IN_1_AND_A_HALF_KILOMETERS.mp3", "およそ1.5キロ先"],
    [ 29, "IN_1_AND_A_HALF_MILES.mp3", "およそ1.5マイル先"],
    [ 30, "IN_1_KILOMETER.mp3", "およそ1キロ先"],
    [ 31, "IN_1_MILE.mp3", "およそ1マイル先"],
    [ 32, "IN_200_FEET.mp3", "およそ200フィート先"],
    [ 33, "IN_200_METERS.mp3", "およそ200メートル先"],
    [ 34, "IN_200_YARDS.mp3", "およそ200ヤード先"],
    [ 35, "IN_2_KILOMETERS.mp3", "およそ2キロ先"],
    [ 36, "IN_2_MILES.mp3", "およそ2マイル先"],
    [ 37, "IN_300_FEET.mp3", "およそ300フィート先"],
    [ 38, "IN_300_METERS.mp3", "およそ300メートル先"],
    [ 39, "IN_300_YARDS.mp3", "およそ300ヤード先"],
    [ 40, "IN_3_KILOMETERS.mp3", "およそ3キロ先"],
    [ 41, "IN_3_MILES.mp3", "およそ3マイル先"],
    [ 42, "IN_400_FEET.mp3", "およそ400フィート先"],
    [ 43, "IN_400_METERS.mp3", "およそ400メートル先"],
    [ 44, "IN_400_YARDS.mp3", "およそ400ヤード先"],
    [ 45, "IN_500_FEET.mp3", "およそ500フィート先"],
    [ 46, "IN_500_METERS.mp3", "およそ500メートル先"],
    [ 47, "IN_50_FEET.mp3", "およそ50フィート先"],
    [ 48, "IN_50_METERS.mp3", "およそ50メートル先"],
    [ 49, "IN_50_YARDS.mp3", "およそ50ヤード先"],
    [ 50, "IN_600_FEET.mp3", "およそ600フィート先"],
    [ 51, "IN_600_METERS.mp3", "およそ600メートル先"],
    [ 52, "IN_800_FEET.mp3", "およそ800フィート先"],
    [ 53, "IN_800_METERS.mp3", "およそ800メートル先"],
    [ 54, "IN_A_HALF_MILE.mp3", "およそ0.5マイル先"],
    [ 55, "IN_A_QUARTER_MILE.mp3", "およそ0.25マイル先"],
    [ 56, "IN_THREE_QUARTERS_OF_A_MILE.mp3", "およそ0.75マイル先"],
    [ 57, "KEEP_LEFT_AT_THE_FORK.mp3", "分岐を左方向です"],
    [ 58, "KEEP_RIGHT_AT_THE_FORK.mp3", "分岐を右方向です"],
    [ 59, "MAKE_A_UTURN.mp3", "Uターンです"],
    [ 60, "NAVIGATION_RESUMED.mp3", "ナビを再開します"],
    [ 61, "PLEASE_DESCRIBE_PROBLEM.mp3", "問題の内容をお話ください"],
    [ 62, "ROUNDABOUT_1ST_EXIT.mp3", "ロータリー1番目の出口です"],
    [ 63, "ROUNDABOUT_EXIT_INTO_THE_RAMP.mp3", "ロータリーからランプに進みます"],
    [ 64, "ROUNDABOUT_TAKE_2ND_EXIT.mp3", "ロータリー2番目の出口です"],
    [ 65, "ROUNDABOUT_TAKE_3RD_EXIT.mp3", "ロータリー3番目の出口です"],
    [ 66, "ROUNDABOUT_TAKE_4TH_EXIT.mp3", "ロータリー4番目の出口です"],
    [ 67, "ROUNDABOUT_TAKE_5TH_EXIT.mp3", "ロータリー5番目の出口です"],
    [ 68, "SHARP_LEFT.mp3", "大きく左方向です"],
    [ 69, "SHARP_RIGHT.mp3", "大きく右方向です"],
    [ 70, "SLIGHT_LEFT.mp3", "斜め左方向です"],
    [ 71, "SLIGHT_LEFT_ONTO_THE_RAMP.mp3", "斜め左方向、入口です"],
    [ 72, "SLIGHT_RIGHT.mp3", "斜め右方向です"],
    [ 73, "SLIGHT_RIGHT_ONTO_RAMP.mp3", "斜め右方向、入口です"],
    [ 74, "TAKE_THE_INTERCHANGE.mp3", "ジャンクションです"],
    [ 75, "TAKE_THE_INTERCHANGE_ON_THE_LEFT.mp3", "左方向、ジャンクションです"],
    [ 76, "TAKE_THE_EXIT.mp3", "出口です"],
    [ 77, "TAKE_THE_EXIT_ON_THE_LEFT.mp3", "左方向、出口です"],
    [ 78, "TAKE_THE_EXIT_ON_THE_RIGHT.mp3", "右方向、出口です"],
    [ 79, "TAKE_THE_FERRY.mp3", "フェリーに乗ります"],
    [ 80, "TAKE_THE_INTERCHANGE_ON_THE_RIGHT.mp3", "右方向、ジャンクションです"],
    [ 81, "TAKE_THE_RAMP.mp3", "入口です"],
    [ 82, "TAKE_THE_TRAIN.mp3", "電車に乗ります"],
    [ 83, "THEN.mp3", "その先"],
    [ 84, "TURN_LEFT.mp3", "左方向です"],
    [ 85, "TURN_LEFT_ONTO_RAMP.mp3", "左方向、入口です"],
    [ 86, "TURN_RIGHT.mp3", "右方向です"],
    [ 87, "TURN_RIGHT_ONTO_THE_RAMP.mp3", "右方向、入口です"],
    [ 88, "WILL_ARRIVE.mp3", "目的地です"],
]

if sys.argv[1] == 'text':
    for x in VOICES:
        print(f"{x[2]}。")
elif sys.argv[1] == 'zip':
    outfile = sys.argv[2]
    wavs = sys.argv[3:]

    with zipfile.ZipFile(outfile, 'w') as zip:
        for wav in wavs:
            m = re.match('.*/([0-9]+)[^/]*', wav)
            index = int(m.group(1))
            mp3_name = VOICES[index - 1][1]
            print(f"add {index}: {wav} -> {mp3_name}")
            seg = pydub.AudioSegment.from_wav(wav)
            mp3 = io.BytesIO()
            seg.export(mp3, format="mp3")
            with zip.open(mp3_name, "w") as zf:
                zf.write(mp3.getvalue())
        plist = io.StringIO()
        print('<?xml version="1.0" encoding="UTF-8"?>', file=plist)
        print('<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">', file=plist)
        print('<plist version="1.0">', file=plist)
        print('<array>', file=plist)
        for v in VOICES:
            print(f'  <dict>', file=plist)
            print(f'    <key>filename</key>', file=plist)
            print(f'    <string>{v[1]}</string>', file=plist)
            print(f'    <key>id</key>', file=plist)
            print(f'    <string>{v[0]}</string>', file=plist)
            print(f'  </dict>', file=plist)
        print('</array>', file=plist)
        print('</plist>', file=plist)
        with zip.open('messages.plist', "w") as zf:
            zf.write(plist.getvalue().encode())
        
        xml = io.StringIO()
        print('<voice_instructions>', file=xml)
        for v in VOICES:
            print(f'  <canned_message id="{v[0]}">{v[1]}</canned_message>', file=xml)
        print('</voice_instructions>', file=xml)
        with zip.open('messages.xml', "w") as zf:
            zf.write(xml.getvalue().encode())
        