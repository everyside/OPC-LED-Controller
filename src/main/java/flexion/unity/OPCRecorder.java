/*
 * Open Pixel Control recording extension for Processing.
 * Record OPC pixel animations to a local file.
 * 
 * flexion 2015
 * 
 * Version: 25.03.2015 - 17:10
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */
package flexion.unity;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import scanlime.OPC;

public class OPCRecorder extends OPC {

	String fileName;
	boolean fileReady;
	boolean inputFileReady;
	boolean recordingFailed;
	String file_header;
	int frameLength;
	int frameRate = 5;
	byte[] bFrame;
	FileOutputStream fos;
	InputStream fis;

	Socket socket;
	OutputStream output; // for fcserver
	String host;
	int port;

	public OPCRecorder(String host, int port, String oFilename) {

		super(host, port);
		this.fileName = oFilename;
	}
	
	public void setFrameRate(int fr) {
		frameRate(fr);
		this.frameRate = fr;
	}

	// -------- RECORDING --------------------------

	void prepareFileForRecording() {
		// Note: length of frame data must be already known when creating the
		// file!
		fileReady = false;
		if (frameLength < 1) {
			System.out
					.println("Unable to record to file, because frame length is unknown or zero");
			return;
		}
		System.out.println("-- Prepare OPC movie output file: " + this.fileName);
		File file = new File(this.fileName);
		try {
			// File header consists of:
			// flx.opc.movie (14 bytes) + [version (1 byte)] + [framerate ms (4
			// byte)] + [framelength (4 byte)] followed by [framedata] +
			// [framedata] + ...
			fos = new FileOutputStream(file);

			// 1) Header format identification
			String hdr = "flx.opc.movie.v001";
			byte[] headerInBytes = hdr.getBytes();
			fos.write(headerInBytes);

			// 2) write "framerate" to header
			byte[] bDelay = ByteBuffer.allocate(4)
					.order(ByteOrder.LITTLE_ENDIAN).putInt(frameRate).array();
			fos.write(bDelay);

			// 3) write length of pixel data frame stream
			byte[] bBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
					.putInt(frameLength).array();
			fos.write(bBuf);

			fileReady = true;

			System.out.println("-- Header written to output file");

		} catch (IOException e) {
			return;
		}
	}

	public void stopRecording() {
		System.out.println("-- Closing output file");
		try {
			if (fos != null)
				fos.close();
		} catch (IOException e) {
			return;
		}
	}

	public void writePixelFrame(byte[] frameData) {
		if (recordingFailed)
			return;
		if (!fileReady) {
			frameLength = frameData.length;
			prepareFileForRecording();
			if (!fileReady) {
				stopRecording();
				recordingFailed = true; // stop processing
				return;
			}
		}
		try {
			fos.write(frameData);
		} catch (IOException e) {
			return;
		}
	}
	
	
	// -------- PLAYBACK --------------------------

	void prepareFileForPlayback() {
		// Note: length of frame data must be already known when creating the
		// file!
		inputFileReady = false;
		
		System.out.println("-- Prepare OPC movie input file: " + this.fileName);
		File file = new File(this.fileName);
		try {
			// File header consists of:
			// flx.opc.movie (14 bytes) + [version (1 byte)] + [framerate ms (4
			// byte)] + [framelength (4 byte)] followed by [framedata] +
			// [framedata] + ...
			fis = new BufferedInputStream(new FileInputStream(file));

			// 1) Header format identification
			String hdr = "flx.opc.movie.v001";
			byte[] formatBytes = new byte[18];
			fis.read(formatBytes, 0, 18);
			
			if(!new String(formatBytes).equals(hdr)){
				throw new RuntimeException("Unexpected Header Format");
			}
			
			// 2) read framerate
			byte[] framerateBytes = new byte[4];
			
			fis.read(framerateBytes);
			setFrameRate(ByteBuffer.wrap(framerateBytes).order(ByteOrder.LITTLE_ENDIAN).getInt());
			System.out.println("Framerate : "+frameRate);

			// 3) write length of pixel data frame stream
			
			byte[] framelengthBytes = new byte[4];
			fis.read(framelengthBytes, 0, 4);
			frameLength = ByteBuffer.wrap(framelengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
			System.out.println("Framelength : "+frameLength);
			
			fis.mark(1000000);
			inputFileReady = true;

			System.out.println("-- Header read from input file");

		} catch (Throwable e) {
			e.printStackTrace();
			return;
		}
	}

	public void stopPlayback() {
		System.out.println("-- Closing input file");
		try {
			if (fis != null)
				fis.close();
		} catch (IOException e) {
			return;
		}
	}

	public byte[] readPixelFrame() {
		if (!inputFileReady) {
			prepareFileForPlayback();
			if (!inputFileReady) {
				stopPlayback();
				return new byte[0];
			}
		}
		try {
			byte[] bytes = new byte[frameLength];
			if(fis.read(bytes, 0, frameLength) > -1){
				return bytes;
			}else{
				fis.reset();
				return readPixelFrame();
			}
		} catch (Throwable e) {

			try {
				fis.reset();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
			return new byte[0];
		}
	}

}
