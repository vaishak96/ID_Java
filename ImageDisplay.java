
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Arrays;
import javax.swing.*;


public class ImageDisplay {

	JFrame frame;
	JLabel lbIm1;
	BufferedImage imgOne;
	int width = 1920; // default image width and height
	int height = 1080;

	String imgPath;
	int subSamplingY;
	int subSamplingU;
	int subSamplingV;
	double scaleW;
	double scaleH;
	int antialiasing;


//	double[][] RGB_TO_YUV = {{0.299, 0.587, 0.114}, {0.596, -0.274, -0.322}, {0.211, -0.523, 0.312}};
//	double[][] YUV_TO_RGB = {{1.000, 0.956, 0.621}, {1.000, -0.272, -0.647}, {1.000, -1.106, 1.703}};


	private void subSampling(float[] arr, int subRate) {
		if (subRate == 1) return;
		for(int i = 0; i < arr.length; i++) {
			if (i % subRate == 0) continue;
			arr[i] = (arr[i - i%subRate] + arr[(i + subRate - i%subRate < arr.length) ? i + subRate - i%subRate:i - i%subRate]) / 2;
		}
	}

	private void fillPixel(int width, int height, float[][] YUVArr, int[][] pixelArr) {
		int idx = 0;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				int r = (int) Math.round(YUVArr[0][idx] * 1.000 + YUVArr[1][idx] * 0.956 + YUVArr[2][idx] * 0.621);
				int g = (int) Math.round(YUVArr[0][idx] * 1.000 + YUVArr[1][idx] * -0.272 + YUVArr[2][idx] * -0.647);
				int b = (int) Math.round(YUVArr[0][idx] * 1.000 + YUVArr[1][idx] * -1.106 + YUVArr[2][idx] * 1.703);
//				System.out.println(Arrays.toString(new int[]{r, g, b}));
				r = Math.min(255, Math.max(0, r));
				g = Math.min(255, Math.max(0, g));
				b = Math.min(255, Math.max(0, b));
				int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
				pixelArr[y][x] = pix;
				idx ++;
			}
		}
	}

	private void YUVToSeparateRGB(int width, int height, float[][] YUVArr, int[][][] RGBArr){
		int idx = 0;
		for(int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int r = (int) Math.round(YUVArr[0][idx] * 1.000 + YUVArr[1][idx] * 0.956 + YUVArr[2][idx] * 0.621);
				int g = (int) Math.round(YUVArr[0][idx] * 1.000 + YUVArr[1][idx] * -0.272 + YUVArr[2][idx] * -0.647);
				int b = (int) Math.round(YUVArr[0][idx] * 1.000 + YUVArr[1][idx] * -1.106 + YUVArr[2][idx] * 1.703);
				RGBArr[y][x][0] = r;
				RGBArr[y][x][1] = g;
				RGBArr[y][x][2] = b;
				idx ++;
			}
		}
	}

	private void fillImg(int width, int height, int[][] pixelArr, BufferedImage img) {
		for(int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				img.setRGB((int)(x*scaleW), (int)(y*scaleH), pixelArr[y][x]);
			}
		}
	}

	private int[] findAverage(int height, int width, int[][][] RGBArr) {
		int cnt = 1;
		int r = RGBArr[height][width][0];
		int g = RGBArr[height][width][1];
		int b = RGBArr[height][width][2];
//		System.out.println("original " + Arrays.toString(new int[] {r, g, b}));
		int[][] dir = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[]pair : dir) {
			int x = width + pair[0], y = height + pair[1];
			if (x < 0 || y < 0 || y == RGBArr.length || x == RGBArr[0].length) continue;
			r += RGBArr[y][x][0];
			g += RGBArr[y][x][1];
			b += RGBArr[y][x][2];
			cnt += 1;
		}
		r /= cnt;
		g /= cnt;
		b /= cnt;
		r = Math.min(255, Math.max(0, r));
		g = Math.min(255, Math.max(0, g));
		b = Math.min(255, Math.max(0, b));
		//		System.out.println("Average " + Arrays.toString(averageRGB));
		return new int[]{r, g, b};
	}

	private void antialiasingFillPixel(int width, int height, int[][][] RGBArr, int[][] pixelArr) {
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				int[] averageRGB = findAverage(y, x, RGBArr);
//				System.out.println(Arrays.toString(averageRGB));
				int pix = 0xff000000 | ((averageRGB[0] & 0xff) << 16) | ((averageRGB[1] & 0xff) << 8) | (averageRGB[2] & 0xff);
				pixelArr[y][x] = pix;
			}
		}
	}


	/** Read Image RGB
	 *  Reads the image of given width and height at the given imgPath into the provided BufferedImage.
	 */
	private void readImageRGB(int width, int height, String imgPath, BufferedImage img, BufferedImage originalImg)
	{
		try
		{
			int frameLength = width*height*3;

			File file = new File(imgPath);
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			raf.seek(0);

			long len = frameLength;
			byte[] bytes = new byte[(int) len];
			float[][] YUVArr = new float[3][width*height];


			raf.read(bytes);

			int idx = 0;
			for(int y = 0; y < height; y++)
			{
				for(int x = 0; x < width; x++)
				{
//					byte a = 0;
					int r = Byte.toUnsignedInt(bytes[idx]);
					int g = Byte.toUnsignedInt(bytes[idx+height*width]);
					int b = Byte.toUnsignedInt(bytes[idx+height*width*2]);
					YUVArr[0][idx] = idx % subSamplingY == 0 ? (float) ( r * 0.299 + g * 0.587 + b * 0.114) : 0;
					YUVArr[1][idx] = idx % subSamplingU == 0 ? (float) (r * 0.596 + g * -0.274 + b * -0.322) : 0;
					YUVArr[2][idx] = idx % subSamplingV == 0 ? (float) (r * 0.211 + g * -0.523 + b * 0.312) : 0;

					int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
					//int pix = ((a << 24) + (r << 16) + (g << 8) + b);
					originalImg.setRGB(x,y,pix);
					idx++;
				}
			}

			subSampling(YUVArr[0], subSamplingY);
			subSampling(YUVArr[1], subSamplingU);
			subSampling(YUVArr[2], subSamplingV);
			int[][] pixelArr = new int[height][width];

			if (antialiasing == 1) {
				int[][][] RGBArr = new int[height][width][3];
				YUVToSeparateRGB(width, height, YUVArr, RGBArr);
				antialiasingFillPixel(width, height, RGBArr, pixelArr);
				fillImg(width, height, pixelArr, img);
			}
			else {
				fillPixel(width, height, YUVArr, pixelArr);
				fillImg(width, height, pixelArr, img);
			}

		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void showIms(String[] args){

		// Read a parameter from command line
		imgPath = args[0];
		subSamplingY =  Integer.parseInt(args[1]);
		subSamplingU = Integer.parseInt(args[2]);
		subSamplingV = Integer.parseInt(args[3]);
		scaleW = Double.parseDouble(args[4]);
		scaleH = Double.parseDouble(args[5]);
		antialiasing = Integer.parseInt(args[6]);
		// Read in the specified image
		imgOne = new BufferedImage((int)(width*scaleW), (int)(height*scaleH), BufferedImage.TYPE_INT_RGB);
		BufferedImage OriginalImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		readImageRGB(width, height, imgPath, imgOne, OriginalImg);

		// Use label to display the image
		frame = new JFrame("Modified Image");
		JFrame originalFrame = new JFrame("Original Image");
		GridBagLayout gLayout = new GridBagLayout();
		frame.getContentPane().setLayout(gLayout);
		originalFrame.getContentPane().setLayout(gLayout);

		lbIm1 = new JLabel(new ImageIcon(imgOne));
		JLabel lbOriginalImg = new JLabel(new ImageIcon(OriginalImg));

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.CENTER;
		c.weightx = 0.5;
		c.gridx = 0;
		c.gridy = 0;

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 1;
		frame.getContentPane().add(lbIm1, c);
		originalFrame.getContentPane().add(lbOriginalImg, c);
		originalFrame.pack();
		originalFrame.setVisible(true);
		frame.pack();
		frame.setVisible(true);
	}


	public static void main(String[] args) {
		ImageDisplay ren = new ImageDisplay();
		if (args.length != 7) {
			System.out.println("Wrong number of sys args\n");
			return;
		}
		ren.showIms(args);
	}
}
