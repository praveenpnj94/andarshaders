package edu.dhbw.andar;

import javax.microedition.khronos.opengles.GL10;

import edu.dhbw.andar.util.GraphicsUtil;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

/**
 * This class defines an ARObject which is used with the GLES20 renderer.
 * In order to use an AndARGLES20Renderer, you need to use ARGLES20Objects with it.  
 * You cannot mix and match these classes.
 * @author Griffin Milsap
 *
 */
public abstract class ARGLES20Object extends ARObject {
	protected AndARGLES20Renderer mRenderer;
	protected int mProgram;
	protected int muMVMatrixHandle;
	protected int muPMatrixHandle;
	protected float[] mMVMatrix = new float[16]; // ModelView Matrix
	protected float[] mPMatrix = new float[16];
	
	public ARGLES20Object(String name, String patternName, double markerWidth, double[] markerCenter, AndARGLES20Renderer renderer) {
		super(name, patternName, markerWidth, markerCenter);
		mRenderer = renderer;
		mProgram = 0;
		muMVMatrixHandle = 0;
		muPMatrixHandle = 0;
	}
	
	/**
	 * Allow the program to draw without dealing with transformations
	 * @param glUnused an unused 1.0 gl context
	 */
	@Override
	public synchronized void draw( GL10 glUnused ) {
		if(!initialized) {
			init(glUnused);
			initialized = true;
		}
		
		// Ensure we're using the program we need
		GLES20.glUseProgram( mProgram );
		
		if( glCameraMatrixBuffer != null) {
			// Transform to where the marker is
			GLES20.glUniformMatrix4fv(muMVMatrixHandle, 1, false, glMatrix, 0);
			GraphicsUtil.checkGlError("glUniformMatrix4fv muMVMatrixHandle");
			GLES20.glUniformMatrix4fv(muPMatrixHandle, 1, false, glCameraMatrix, 0);
			GraphicsUtil.checkGlError("glUniformMatrix4fv muPMatrixHandle");
		}
		
		// Let the object draw
		drawGLES20();
	}
	
	@Override
	public synchronized void predraw( GL10 glUnused ) {
		if(!initialized) {
			init(glUnused);
			initialized = true;
		}
		
		// Ensure we're using the program we need
		GLES20.glUseProgram( mProgram );
		
		if( glCameraMatrixBuffer != null) {
			// Transform to where the marker is
			GLES20.glUniformMatrix4fv(muMVMatrixHandle, 1, false, glMatrix, 0);
			GraphicsUtil.checkGlError("glUniformMatrix4fv muMVMatrixHandle");
			GLES20.glUniformMatrix4fv(muPMatrixHandle, 1, false, glCameraMatrix, 0);
			GraphicsUtil.checkGlError("glUniformMatrix4fv muPMatrixHandle");
		}
		
		// Allow the object to predraw
		predrawGLES20();
	}

	/**
	 * Initialize the shader and transform matrix attributes
	 * @param glUnused an unused 1.0 gl context
	 */
	@Override
	public void init( GL10 glUnused ) {
		setProgram( vertexProgramPath(), fragmentProgramPath() );
		initGLES20();
	}
	
	/**
	 * Compile and load a vertex and fragment program for this object
	 * @param vspath Path relative to the "assets" directory which denotes location of the vertex shader
	 * @param fspath Path relative to the "assets" directory which denotes location of the fragment shader
	 */
	public void setProgram( String vspath, String fspath )
	{
		// Load and compile the program, grab the attribute for transformation matrix
		mProgram = GraphicsUtil.loadProgram( mRenderer.activity, vspath, fspath );
		muMVMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVMatrix");
        GraphicsUtil.checkGlError("ARGLES20Object glGetUniformLocation uMVMatrix");
        if (muMVMatrixHandle == -1) {
            throw new RuntimeException("Requested shader does not have a uniform named uMVMatrix");
        }
        muPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uPMatrix");
        GraphicsUtil.checkGlError("ARGLES20Object glGetUniformLocation uPMatrix");
        if (muPMatrixHandle == -1) {
            throw new RuntimeException("Requested shader does not have a uniform named uPMatrix");
        }
	}
	
	/**
	 * Calculates a screen space bounding box from an axis aligned bounding box
	 * @param aabb [minx][miny][minz][maxx][maxy][maxz] -- see GraphicsUtil.calcAABB()
	 * @return normalized screen space bounding box, [minx][miny][maxx][maxy]
	 */
	public float[] calcSSBB( float[] aabb ) {
		// http://www.opengl.org/sdk/docs/man/xhtml/gluProject.xml
		float[] t = { 0.0f, 0.0f, 0.0f, 1.0f };
		float[] ssbb = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE };
		float[] MVPMat = new float[16];
		Matrix.multiplyMM(MVPMat, 0, glCameraMatrix, 0, glMatrix, 0);
		float[] res = new float[4];
		for( int i = 0; i < 8; i++ )
		{
			// Test all points in the aabb
			t[0] = ( ( i / 4 ) % 2 == 0 ) ? aabb[0] : aabb[3];
			t[1] = ( ( i / 2 ) % 2 == 0 ) ? aabb[1] : aabb[4];
			t[2] = ( i % 2 == 0 ) ? aabb[2] : aabb[5];
			t[3] = 1.0f;
			
			// Project the point by the ModelView matrix then the Projection Matrix
			Matrix.multiplyMV(res, 0, MVPMat, 0, t, 0);
			
			// Save mins and maxs
			float x = ( ( res[0] / res[3] ) + 1.0f ) * 0.5f;
			float y = ( ( res[1] / res[3] ) + 1.0f ) * 0.5f;
			if( x < ssbb[0] ) ssbb[0] = x;
			if( x > ssbb[2] ) ssbb[2] = x;
			if( y < ssbb[1] ) ssbb[1] = y;
			if( y > ssbb[3] ) ssbb[3] = y;
		}
		
		// Clamp SSBB from 0.0 to 1.0
		if( ssbb[0] < 0.0f ) ssbb[0] = 0.0f; if( ssbb[0] > 1.0f ) ssbb[0] = 1.0f;
		if( ssbb[1] < 0.0f ) ssbb[1] = 0.0f; if( ssbb[1] > 1.0f ) ssbb[1] = 1.0f;
		if( ssbb[2] < 0.0f ) ssbb[2] = 0.0f; if( ssbb[2] > 1.0f ) ssbb[2] = 1.0f;
		if( ssbb[3] < 0.0f ) ssbb[3] = 0.0f; if( ssbb[3] > 1.0f ) ssbb[3] = 1.0f;
		return ssbb;
	}

	/**
	 * Generates a cubemap for this object and puts it in graphics memory
	 * @param vertices A float array of vertices: [x][y][z][x][y][z]...
	 */
	public void GenerateCubemap( float[] vertices ) {
		float[] aabb = GraphicsUtil.calcAABB( vertices );
		//Log.d("ARGLES20Object", "AABB: Min: ( " + aabb[0] + ", " + aabb[1] + ", " + aabb[2] + " ), Max: ( " + aabb[3] + ", " + aabb[4] + ", " + aabb[5] + " ) " );
		float[] ssbb = calcSSBB( aabb );
		//Log.d("ARGLES20Object", "SSBB: Min: ( " + ssbb[0] + ", " + ssbb[1] + " ), Max: ( " + ssbb[2] + ", " + ssbb[3] + ") " );
		float[] ssbbverts = {
				ssbb[0], ssbb[1], 0.0f,
				ssbb[2], ssbb[1], 0.0f,
				ssbb[0], ssbb[3], 0.0f,
				ssbb[2], ssbb[3], 0.0f
		};
		//mRenderer.mDebugDraw.debugTriangleStrip( ssbbverts );
		mRenderer.generateCubemap( ssbb );
	}
	
	
	/**
	 * Implement this method and setup GL to render your object here
	 */
	public abstract void initGLES20();
	
	/**
	 * Implement this method to do all RTT and pre-draw stuff before the draw to the screen
	 */
	public abstract void predrawGLES20();
	
	/**
	 * Implement this method and draw your object within it
	 */
	public abstract void drawGLES20();
	
	/**
	 * Return the path relative to the "assets" directory for the vertex program
	 */
	public abstract String vertexProgramPath();
	
	/**
	 * Return the path relative to the "assets" directory for the fragment program
	 */
	public abstract String fragmentProgramPath();
}
