require 'active_support/all'
require 'capybara/rspec'
require 'chromedriver/helper'
require 'logger'
require 'pry'
require 'selenium-webdriver'

require 'helpers/misc'

Chromedriver.set_version "2.29"

APP_ROOT_DIR = \
  Pathname(File.dirname(File.absolute_path(__FILE__))).parent

APP_BINARY = \
  case RUBY_PLATFORM
  when 'x86_64-darwin16', 'arm64-darwin22'
    APP_ROOT_DIR.join(
      'madek-exporter-darwin-x64/madek-exporter.app/Contents/MacOS/madek-exporter').to_s
  when 'x86_64-linux', 'x86_64-linux-gnu'
    APP_ROOT_DIR.join( 'madek-exporter-linux-x64/madek-exporter').to_s
  else
    raise "#{RUBY_PLATFORM} system not configured / not supported"
  end

RSpec.configure do |config|
  config.include Helpers::Misc

  Capybara.register_driver :chrome do |app|
    diver = Capybara::Selenium::Driver.new(
      app,
      :browser => :chrome,
      :desired_capabilities => Selenium::WebDriver::Remote::Capabilities.chrome(
        'chromeOptions' => {
          'binary' => APP_BINARY,
          "args" => [ "--verbose",
                      "--log-path=#{ENV['CIDER_CI_WORKING_DIR']}/logs/chromedriver.log"]
        }))
  end

  config.before(:all) do |example|
    Capybara.default_driver = :chrome
  end

  config.before(:each) do |example|
    Capybara.default_driver = :chrome

    # hack to get to the start page;
    # capybara requires us to `visit` something; otherwise there will be no window
    visit 'about:blank'
    execute_script 'window.history.back()'

  end


    config.after(:each) do |example|
      take_screenshot unless example.exception.nil?
    end

    def take_screenshot(screenshot_dir = nil, name = nil)
      screenshot_dir ||= File.join(Dir.pwd, 'tmp')
      begin
        Dir.mkdir screenshot_dir
      rescue
        nil
      end
      name ||= "screenshot_#{Time.now.iso8601.gsub(/:/, '-')}.png"
      path = File.join(screenshot_dir, name)
      case Capybara.current_driver
      when :selenium, :selenium_chrome, :chrome
        begin
          page.driver.browser.save_screenshot(path)
        rescue
          nil
        end
      else
        Rails.logger.warn 'Taking screenshots is not implemented for ' \
        "#{Capybara.current_driver}."
      end
    end



end


