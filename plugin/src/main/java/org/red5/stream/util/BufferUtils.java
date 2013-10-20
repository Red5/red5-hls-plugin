/*
 * RED5 HLS plugin - https://github.com/mondain/red5-hls-plugin
 * 
 * Copyright 2006-2013 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.stream.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BufferUtils {

	/**
	 * FloatShortsToBytes
	 * 
	 * Convert a float array to short array.
	 * 
	 * After that, convert 16-bit integer (short) into two bytes.
	 * 
	 */
	public static byte[] floatsShortsToBytes(float[] fData) {
		int total = fData.length;
		short[] sData = new short[total];
		for (int i = 0; i < total; i++) {
			sData[i] = (short) fData[i];
		}
		byte[] bytes2 = new byte[fData.length * 2];
		ByteBuffer.wrap(bytes2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(sData);
		return bytes2;
	}

	/**
	 * Converts a byte array into a short array. Since a byte is 8-bits,
	 * and a short is 16-bits, the returned short array will be half in
	 * length than the byte array. If the length of the byte array is odd,
	 * the length of the short array will be
	 * <code>(byteArray.length - 1)/2</code>, i.e., the last byte is discarded.
	 *
	 * @param byteArray a byte array
	 * @param offset which byte to start from
	 * @param length how many bytes to convert
	 * @param little specifies whether the result should be using little endian
	 * order or not.
	 *
	 * @return a short array, or <code>null</code> if byteArray is of zero length
	 *
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 */
	public static short[] byteToShortArray(byte[] byteArray, int offset, int length, boolean little) throws ArrayIndexOutOfBoundsException {
		if (0 < length && (offset + length) <= byteArray.length) {
			int shortLength = length / 2;
			short[] shortArray = new short[shortLength];
			int temp;
			for (int i = offset, j = 0; j < shortLength; j++, temp = 0x00000000) {
				if (little) {
					temp = byteArray[i++] & 0x000000FF;
					temp |= 0x0000FF00 & (byteArray[i++] << 8);
				} else {
					temp = byteArray[i++] << 8;
					temp |= 0x000000FF & byteArray[i++];
				}
				shortArray[j] = (short) temp;
			}
			return shortArray;
		} else {
			throw new ArrayIndexOutOfBoundsException("offset: " + offset + ", length: " + length + ", array length: " + byteArray.length);
		}
	}

}
