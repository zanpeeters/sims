try:
    from .sims import *
    from .utils import *
    from .rkeys import *
except SystemError:
    from sims import *
    from utils import *
    from rkeys import *  

__version__ = '20140429'
