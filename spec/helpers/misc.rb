require 'timeout'

module Helpers
  module Misc
    extend self

    def wait_until(wait_time = 5, &block)
      begin
        Timeout.timeout(wait_time) do
          until value = block.call
            sleep(1)
          end
          value
        end
      rescue Timeout::Error => e
        fail Timeout::Error.new(block.source)
      end
    end

  end
end


