package imagej.data;

import imagej.StorageType;

public class ByteType implements Type
{
	@Override
	public String getName()
	{
		return "8-bit signed";
	}

	@Override
	public boolean isFloat()
	{
		return false;
	}

	@Override
	public boolean isUnsigned()
	{
		return false;
	}

	@Override
	public int getNumBitsData()
	{
		return 8;
	}

	@Override
	public double getMinReal() 
	{
		return Byte.MIN_VALUE;
	}

	@Override
	public double getMaxReal()
	{
		return Byte.MAX_VALUE;
	}

	@Override
	public long getMinIntegral()
	{
		return Byte.MIN_VALUE;
	}

	@Override
	public long getMaxIntegral()
	{
		return Byte.MAX_VALUE;
	}

	@Override
	public DataAccessor allocateArrayAccessor(Object array)
	{
		Types.verifyCompatibility(this, array);

		return new ByteArrayAccessor(array);
	}

	@Override
	public StorageType getStorageType()
	{
		return StorageType.INT8;
	}

	@Override
	public double getNumberOfStorageTypesPerValue()
	{
		return 1;
	}

	@Override
	public boolean isStorageCompatible(Object data)
	{
		return data instanceof byte[];
	}

	@Override
	public long calcNumStorageBytesFromPixelCount(long numPixels)
	{
		return 1 * calcNumStorageUnitsFromPixelCount(numPixels);
	}

	@Override
	public long calcNumStorageUnitsFromPixelCount(long numPixels)
	{
		return numPixels;
	}

	@Override
	public Object allocateStorageArray(long numPixels)
	{
		long numStorageUnits = calcNumStorageUnitsFromPixelCount(numPixels);
		
		if (numStorageUnits > Integer.MAX_VALUE)
			throw new IllegalArgumentException("more storage units requested ("+numStorageUnits+") than Java can allocate ("+Integer.MAX_VALUE+")");
		
		return new byte[(int)numStorageUnits];
	}

}
