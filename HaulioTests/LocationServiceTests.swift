import XCTest
@testable import Haulio

@MainActor
final class LocationServiceTests: XCTestCase {
    private var service: LocationService!

    override func setUp() {
        super.setUp()
        service = LocationService()
    }

    override func tearDown() {
        service = nil
        super.tearDown()
    }

    func testServiceInitializesWithoutCrash() {
        // Verify service can be created without errors
        XCTAssertNotNil(service)
    }

    func testStartTrackingReturnsStream() {
        // Verify we can create an AsyncStream without crash
        let stream = service.startTracking()
        XCTAssertNotNil(stream)
        // Clean up
        service.stopTracking()
    }

    func testStopTrackingDoesNotCrash() {
        // Calling stop before start should be safe
        service.stopTracking()
        XCTAssertNotNil(service)
    }

    func testAuthorizationStatusAccessible() {
        // Should return a valid status (not crash)
        let status = service.authorizationStatus
        XCTAssertNotNil(status)
    }
}
