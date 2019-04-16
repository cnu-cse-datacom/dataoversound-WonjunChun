package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone(){

        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();

    }

    public void PreRequest(){
        //여기를 채워야 함(메시지 받기 버튼 눌렀을 시 동작하는 함수)
        //받은 소리를 푸리에 변환을 하고(ppt 12p)
        //푸리에 트랜스폼 결과(허수)를 실수로 변환 후 주파수 정규화 해주어야 함
        //그리고 로그 출력
        int blocksize = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
        short[] buffer = new short[blocksize];
        //int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);

        boolean in_packet = false;
        ArrayList<Integer> packet = new ArrayList<Integer>();

        while(true){
            int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);
            //버퍼에 소리가 들어감
            double[] chunk = new double[buffer.length];//findFrequency에 double[]로 전달해줘야 하므로

            for(int i = 0; i < buffer.length; i++){
                chunk[i] = (double)buffer[i];//형변환
           }

            double dom = findFrequency(chunk);//dominant
            if(in_packet && match(dom, HANDSHAKE_END_HZ)){
                ArrayList<Integer> byte_stream = extract_packet(packet);

                String str = "";
                for(int i = 0; i < byte_stream.size(); i++){
                    str += Integer.toString(byte_stream.get(i));
                    //Log.d("ListenToneCHUNK", Integer.toString(byte_stream.get(i)));
                }
                Log.d("ListenToneCHUNK", str);

                packet.clear();
                in_packet = false;

            }
            else if(in_packet){
                packet.add((int)dom);

            }
            else if(match(dom, HANDSHAKE_START_HZ)){
                in_packet = true;
            }
        }
    }

    public int findPowerSize(int bufferSize){//버퍼 사이즈는 2의 제곱수 형태로 들어가야됨
        //2의 제곱수가 아니면 그 수보다 큰 2의 제곱수 중 가장 가까운 값 가져와야됨
        int powerSize = 1;
        for(int i = 0; i < bufferSize; i++){//
            if(bufferSize < Math.pow(2, i)){
                powerSize = (int)Math.pow(2, i);
                break;
            }
        }
        return powerSize;
    }

    private double findFrequency(double[] toTransform){//dominant 함수와 비슷
        int len = toTransform.length;
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];

        Complex[] complx = transform.transform(toTransform, TransformType.FORWARD); //푸리에 변환한 복소수
        Double[] freq = this.fftfreq(complx.length, 1);

        for(int i = 0; i < complx.length; i++){
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));//복소수 값을 실수화
        }

        //최고주파수 추출
        double peak_coeff = 0.0;
        int peak_coeff_index = 0;
        double peak_freq;

        for(int i = 0; i < mag.length; i++){
            if(peak_coeff < Math.abs(mag[i])) {
                peak_coeff = Math.abs(mag[i]);
                peak_coeff_index = i;
            }
        }

        peak_freq = freq[peak_coeff_index];

        return Math.abs(peak_freq * mSampleRate);//in Hz

    }

    private Double[] fftfreq(int length, int duration){
        Double[] freq = new Double[length];
        double d_n = duration * length;
        if(length % 2 != 0)
            length = length - 1;//길이가 홀수이면, 짝수길이로
        for(int i = 0; i < length/2; i++){
            freq[i] = Double.valueOf(i/d_n);
        }
        for(int i = length/2+1; i < length; i++){
            freq[i] = Double.valueOf(-(length-i)/d_n);
        }

        return freq;
    }

    private boolean match(double freq1, double freq2){
        return Math.abs(freq1 - freq2) < 20;
    }

    private ArrayList<Integer> extract_packet(ArrayList<Integer> freqs){ //int형 아닐수 있음
        //double[] temp_freqs = new double[freqs.length/2];
        ArrayList<Integer> temp_freqs = new ArrayList<Integer>();
        /*for(int i = 0; i < temp_freqs.length; i++){
            temp_freqs[i] = freqs[2*i];
        }*/
        for(int i = 0; i < freqs.size()/2; i++){
            temp_freqs.add(freqs.get(2*i));
        }

        //temp_freqs.remove(0);//시작주파수 제거

        //int[] bit_chunks = new int[temp_freqs.length];
        ArrayList<Integer> bit_chunks = new ArrayList<Integer>();

        /*for(int i = 0; i < temp_freqs.length; i++){
            bit_chunks[i] = (int)Math.round((temp_freqs[i] - START_HZ)/STEP_HZ);

        }*/
        for(int i = 0; i < temp_freqs.size(); i++){
            bit_chunks.add(Math.round((temp_freqs.get(i)-START_HZ)/STEP_HZ));//오류 시 형변환 해주기
        }
        bit_chunks.remove(0);//시작주파수 제거
        for(int i = temp_freqs.size()-1; i > 0; i--){
            if(bit_chunks.get(i) < 0 || bit_chunks.get(i) >= 16)
                bit_chunks.remove(i);
        }

        return decode_bitchunks(BITS, bit_chunks);
    }

    private ArrayList<Integer> decode_bitchunks(int chunk_bits, ArrayList<Integer> chunks){
        ArrayList<Integer> out_bytes = new ArrayList<Integer>();
        /*
        for(int i = 0; i < chunks.size(); i++){
            int input = chunks.get(i) << 4 + chunks.get(i);
            out_bytes.add(input);
        }*/

        int next_read_chunk = 0;
        int next_read_bit = 0;

        int input_byte = 0;
        int bits_left = 8;

        while(next_read_chunk < chunks.size()){
            int can_fill = chunk_bits - next_read_bit;
            int to_fill = Math.min(bits_left, can_fill);
            int offset = chunk_bits - next_read_bit - to_fill;
            input_byte <<= to_fill;
            int shifted = chunks.get(next_read_chunk) & (((1 << to_fill)-1) << offset);
            input_byte |= shifted >> offset;
            bits_left -= to_fill;
            if(bits_left <= 0){
                out_bytes.add(input_byte);
                input_byte = 0;
                bits_left = 8;

            }
            if(next_read_bit >= chunk_bits){
                next_read_chunk += 1;
                next_read_bit -= chunk_bits;
            }

        }

        return out_bytes;

    }




}
