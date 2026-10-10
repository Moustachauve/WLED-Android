import Testing
@testable import WLED

struct SemanticVersionTests {

    @Test func validSemanticVersions() throws {
        let v1 = try #require(SemanticVersion("0.15.0"))
        #expect(v1.major == 0)
        #expect(v1.minor == 15)
        #expect(v1.patch == 0)
        #expect(v1.preRelease == nil)
        
        let v2 = try #require(SemanticVersion("v1.2.3"))
        #expect(v2.major == 1)
        #expect(v2.minor == 2)
        #expect(v2.patch == 3)
        
        let v3 = try #require(SemanticVersion("0.16.0-b2"))
        #expect(v3.major == 0)
        #expect(v3.minor == 16)
        #expect(v3.patch == 0)
        #expect(v3.preRelease == "b2")
        
        let v4 = try #require(SemanticVersion(" 0.14.2 "))
        #expect(v4.major == 0)
        #expect(v4.minor == 14)
        #expect(v4.patch == 2)
    }
    
    @Test func invalidSemanticVersions() {
        #expect(SemanticVersion("invalid") == nil)
        #expect(SemanticVersion("v1") == nil)
        #expect(SemanticVersion("") == nil)
    }

    @Test func versionComparison() throws {
        let v0_14 = try #require(SemanticVersion("0.14.0"))
        let v0_14_1 = try #require(SemanticVersion("0.14.1"))
        let v0_15 = try #require(SemanticVersion("0.15.0"))
        let v0_15_b1 = try #require(SemanticVersion("0.15.0-b1"))
        let v0_15_b2 = try #require(SemanticVersion("0.15.0-b2"))
        let v0_16 = try #require(SemanticVersion("0.16.0"))
        
        // Basic inequalities
        #expect(v0_14 < v0_14_1)
        #expect(v0_14_1 < v0_15)
        #expect(v0_15 < v0_16)
        
        // Pre-release vs Stable
        #expect(v0_15_b1 < v0_15)
        #expect(v0_15 > v0_15_b1)
        
        // Pre-release vs Pre-release
        #expect(v0_15_b1 < v0_15_b2)
        
        // Equality
        #expect(v0_16 == (try #require(SemanticVersion("v0.16.0"))))
        #expect(v0_16 >= (try #require(SemanticVersion("0.16.0"))))
        #expect(v0_15 >= v0_15_b2)
        #expect(v0_16 >= v0_15)
    }
}
