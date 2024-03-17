import pytest

HOST = 'localhost'
PORT = 4970

# Init client
@pytest.fixture(scope="module")
def getClient():
    import tftpclient
    return tftpclient.TFTPClient((HOST, PORT), './read/')

@pytest.fixture(scope="module")
def putClient():
    import tftpclient
    return tftpclient.TFTPClient((HOST, PORT), './read/')


# Get existing 50 byte file
def test_GSBSmall(getClient):
    assert getClient.getFile(b'f50b.bin')


# Get existing 500 byte file
def test_GSBLarge(getClient):
    assert getClient.getFile(b'f500b.bin')


# Get existing 1,535 byte file
##def test_GMB3(getClient):
##    assert getClient.getFile(b'f3blks.bin')


# Get existing 262,143 byte file
##def test_GMB512(getClient):
##    assert getClient.getFile(b'f512blks.bin')


# Put 50 byte file
def test_PSB50B(putClient):
    assert putClient.putFileBytes(b'f50b.ul', 50)


# Put 500 byte file
def test_PSB500B(putClient):
    assert putClient.putFileBytes(b'f500b.ul', 500)


# Put 512 byte file
##def test_PMB1Blks(putClient):
##    assert putClient.putFileBlocks(b'f1blk.ul', 1)


# Put 1,536 byte file
##def test_PMB3Blks(putClient):
##    assert putClient.putFileBlocks(b'f3blks.ul', 3)


# Put 262,144 byte file
##def test_PMB512Blks(putClient):
##    assert putClient.putFileBlocks(b'f512blks.ul', 512)

# Try to get a file that does not exist
def test_GFileNotExists(getClient):
    assert getClient.getFileNotExists(b'nosuchfile')

# Send unknown request type
def test_BadOp10(getClient):
    assert getClient.sendBadOp(10)

# Send an unknown request type (similar to an existing)
def test_BadOp257(getClient):
    assert getClient.sendBadOp(257)


# Get a large file and fail the first ACK every time
##def test_GMBFail1stAck(getClient):
##    assert getClient.getMultiBlockFileFailAck(b'f3blks.bin', 1)


# Get a large file and fail the first two ACKs every time
##def test_GMBFail2ndAck(getClient):
##    assert getClient.getMultiBlockFileFailAck(b'f3blks.bin', 2)
