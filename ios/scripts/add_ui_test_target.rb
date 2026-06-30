#!/usr/bin/env ruby
# Adds (idempotently) an `iosUITests` UI-testing target to ios.xcodeproj and a shared
# `iosUITests` scheme that runs it. Driven by the xcodeproj gem so we don't hand-edit the
# project.pbxproj. Re-running replaces the target/scheme cleanly.
#
# Usage:  ruby ios/scripts/add_ui_test_target.rb
require 'xcodeproj'

ROOT = File.expand_path('..', __dir__)             # repo/ios
PROJECT_PATH = File.join(ROOT, 'ios.xcodeproj')
APP_TARGET_NAME = 'ios'
TEST_TARGET_NAME = 'iosUITests'

project = Xcodeproj::Project.open(PROJECT_PATH)

app_target = project.targets.find { |t| t.name == APP_TARGET_NAME }
raise "App target '#{APP_TARGET_NAME}' not found" unless app_target

# --- Remove any prior test target so the script is idempotent --------------------------------
project.targets.select { |t| t.name == TEST_TARGET_NAME }.each do |t|
  project.root_object.attributes['TargetAttributes']&.delete(t.uuid)
  t.remove_from_project
end

# --- Create the UI-testing target ------------------------------------------------------------
test_target = project.new_target(:ui_test_bundle, TEST_TARGET_NAME, :ios, '16.0')

test_target.build_configurations.each do |config|
  bs = config.build_settings
  bs['PRODUCT_BUNDLE_IDENTIFIER'] = 'com.darkrockstudios.apps.hammer.iosUITests'
  bs['PRODUCT_NAME'] = '$(TARGET_NAME)'
  bs['TEST_TARGET_NAME'] = APP_TARGET_NAME            # the app under test
  bs['INFOPLIST_FILE'] = 'iosUITests/Info.plist'
  bs['SWIFT_VERSION'] = '5.0'
  bs['IPHONEOS_DEPLOYMENT_TARGET'] = '16.0'
  bs['TARGETED_DEVICE_FAMILY'] = '1,2'
  bs['CODE_SIGN_STYLE'] = 'Automatic'                 # simulator runs need no manual profile
  bs['DEVELOPMENT_TEAM'] = ''
  bs['ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES'] = 'YES'
  bs['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks', '@loader_path/Frameworks']
end

# --- Wire the test bundle to its host app ----------------------------------------------------
test_target.add_dependency(app_target)
attrs = (project.root_object.attributes['TargetAttributes'] ||= {})
attrs[test_target.uuid] = { 'TestTargetID' => app_target.uuid }

# --- Source files: rebuild the iosUITests group from what's on disk ---------------------------
group = project.main_group.children.find { |c| c.respond_to?(:display_name) && c.display_name == TEST_TARGET_NAME }
group ||= project.main_group.new_group(TEST_TARGET_NAME, TEST_TARGET_NAME)
group.files.dup.each(&:remove_from_project)

swift_files = Dir.glob(File.join(ROOT, TEST_TARGET_NAME, '*.swift')).sort
raise 'No Swift test files found' if swift_files.empty?
swift_files.each do |path|
  ref = group.new_file(File.basename(path))
  test_target.add_file_references([ref])
end

# --- Shared scheme that runs the UI tests ----------------------------------------------------
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app_target)
scheme.add_test_target(test_target)
scheme.set_launch_target(app_target)
scheme.test_action.build_configuration = 'Debug'
scheme.launch_action.build_configuration = 'Debug'
scheme.save_as(PROJECT_PATH, TEST_TARGET_NAME, true) # shared = true

project.save

puts "OK: created target '#{TEST_TARGET_NAME}' (#{swift_files.length} test files) + shared scheme."
puts "Test files: #{swift_files.map { |f| File.basename(f) }.join(', ')}"
